package akkaviz.frontend.components

import akkaviz.frontend.DOMGlobalScope
import akkaviz.frontend.FrontendUtil.isTemporaryActor
import org.scalajs.dom.html.Element
import rx.Var

import scala.concurrent.duration.{FiniteDuration, _}
import scala.scalajs.js
import scala.scalajs.js.timers.SetTimeoutHandle

class GraphView(showUnconnected: Var[Boolean]) extends Component {

  override def render: Element = {

    //FIXME: port initialization of graph from JS to scala and do it here
    js.undefined.asInstanceOf
  }

  private val graph = DOMGlobalScope.graph
  private val nodes = js.Dictionary[js.Dictionary[js.Any]]()
  private val connectedNodes = js.Dictionary[Unit]()
  private val createdLinks = js.Dictionary[Unit]()

  private val updateFrequency: FiniteDuration = 500.millis

  private var scheduler: js.UndefOr[SetTimeoutHandle] = js.undefined

  private def isNodeConnected(node: String): Boolean = {
    connectedNodes.contains(node)
  }

  showUnconnected.triggerLater {
    val show = showUnconnected.now
    if (show) {
      nodes.foreach {
        case (node, data) =>
          enqueueOperation(GraphView.AddNode(node, data))
      }
    } else {
      nodes.foreach {
        case (node, data) if !isNodeConnected(node) =>
          enqueueOperation(GraphView.RemoveNode(node))
        case _ => //do nothing
      }
    }
  }

  private def scheduleGraphOperations(): Unit = {
    if (scheduler.isEmpty) {
      val timer: SetTimeoutHandle = scala.scalajs.js.timers.setTimeout(updateFrequency) {
        scheduler = js.undefined
        applyGraphOperations()
      }
      scheduler = timer
    }
  }

  private def applyGraphOperations(): Unit = {
    graph.beginUpdate()
    operationsToApply.foreach {
      case GraphView.AddLink(from, to, id) =>
        graph.addLink(from, to, id)
      case GraphView.AddNode(node, dataMaybe) =>
        graph.addNode(node, dataMaybe)
      case GraphView.RemoveNode(node) =>
        graph.removeNode(node)
    }
    operationsToApply = js.Array()
    graph.endUpdate()
  }

  private var operationsToApply = js.Array[GraphView.GraphOperation]()

  private def enqueueOperation(op: GraphView.GraphOperation): Unit = {
    operationsToApply.append(op)
    scheduleGraphOperations()
  }

  def ensureGraphLink(sender: String, receiver: String, nodesLabeler: (String) => String): Unit = {
    val linkId = s"${sender}->${receiver}"
    ensureNodeExists(sender, nodesLabeler(sender))
    ensureNodeExists(receiver, nodesLabeler(receiver))
    if (notTemporary(sender, receiver) && !createdLinks.contains(linkId)) {
      createdLinks.update(linkId, ())
      connectedNodes.update(sender, ())
      connectedNodes.update(receiver, ())
      enqueueOperation(GraphView.AddLink(sender, receiver, linkId))
    }
  }

  private def notTemporary(refA: String, refB: String) =
    !isTemporaryActor(refA) && !isTemporaryActor(refB)

  def ensureNodeExists(node: String, label: String, data: js.Dictionary[js.Any] = js.Dictionary()): Unit = {
    if (!isTemporaryActor(node)) {
      data.update("label", label)
      nodes.update(node, data)
      if (showUnconnected.now || isNodeConnected(node))
        enqueueOperation(GraphView.AddNode(node, data))
    }
  }

}

case object GraphView {

  sealed trait GraphOperation

  case class AddLink(from: String, to: String, linkId: String) extends GraphOperation

  case class AddNode(node: String, data: js.UndefOr[js.Any] = js.undefined) extends GraphOperation

  case class RemoveNode(node: String) extends GraphOperation

}
