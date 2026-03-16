// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.build

import com.intellij.ide.rpc.weakRpcId
import com.intellij.ide.ui.icons.rpcId
import com.intellij.openapi.application.EDT
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.util.containers.nullize
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

private val LOG = fileLogger()

@ApiStatus.Internal
class BuildTreeViewModel(private val consoleView: BuildTreeConsoleView, private val scope: CoroutineScope) {
  private val eventFlow = BuildTreeEventFlow(scope, consoleView.rootElement, consoleView.buildProgressRootNode)
  private val navigationFlow = MutableSharedFlow<BuildTreeNavigationRequest>(extraBufferCapacity = Int.MAX_VALUE)

  val id: BuildViewId = this.storeGlobally(scope)

  @Volatile
  private var hasSelection = false
  @Volatile
  private var hasPrevNode = false
  @Volatile
  private var hasNextNode = false
  @Volatile
  private var hasAnyNode = false
  @Volatile
  private var clearingSelection = false

  fun getTreeEventsFlow(): Flow<BuildTreeEvent> = eventFlow.getFlowWithHistory()

  fun createOrUpdateNodes(nodes: Collection<ExecutionNode>) {
    assert(consoleView.isCorrectThread) // it should be ok to extract information from the passed nodes here
    if (nodes.isEmpty()) return
    val nodesWithData = nodes.associateWith { it.toDto() }
    eventFlow.emitNodeUpdates(nodesWithData)
  }

  fun clearNodes() {
    eventFlow.clearNodes()
  }

  fun clearSelection() {
    if (!hasSelection) {
      LOG.debug("No selection, no need to clear")
      return
    }
    clearingSelection = true
    expose(null, false)
  }

  fun makeVisible(node: ExecutionNode, alsoSelect: Boolean) {
    expose(node, alsoSelect)
  }

  private fun expose(node: ExecutionNode?, alsoSelect: Boolean) {
    eventFlow.expose(node, alsoSelect)
  }

  private fun updateFilteringState() {
    eventFlow.updateFilteringState(BuildTreeFilteringState(showingSuccessful, showingWarnings))
  }

  fun getNodeById(id: Int): ExecutionNode? {
    return when(id) {
      BuildTreeNode.ROOT_ID -> consoleView.rootElement
      BuildTreeNode.BUILD_PROGRESS_ROOT_ID -> consoleView.buildProgressRootNode
      else -> eventFlow.id2Node[id]
    }
  }

  private fun ExecutionNode.toDto(): BuildTreeNode {
    return BuildTreeNode(-1, // to be filled later
                         -1, // to be filled later
                         currentIcon?.rpcId(),
                         title,
                         name,
                         currentHint,
                         getBuildDuration(),
                         navigatables.mapNotNull { it.weakRpcId() },
                         isAutoExpandNode,
                         hasWarnings() || isFailed,
                         isAlwaysVisible || consoleView.isAlwaysVisible(this),
                         SUCCESSFUL_STEPS_FILTER.test(this),
                         WARNINGS_FILTER.test(this))
  }

  private fun ExecutionNode.getBuildDuration(): BuildDuration? {
    val start = startTime
    val end = endTime
    if (start == end) return null
    return if (isRunning) {
      if (start == 0L)
        BuildDuration.Fixed(0)
      else
        BuildDuration.InProgress(start)
    }
    else {
      if (ExecutionNode.isSkipped(result))
        null
      else
        BuildDuration.Fixed(end - start)
    }
  }

  fun onSelectionChange(selectedNodeId: Int?) {
    this.clearingSelection = false
    this.hasSelection = selectedNodeId != null
    if (selectedNodeId != null) {
      val node = getNodeById(selectedNodeId)
      if (node == null) {
        LOG.warn("Couldn't find selected node (id=$selectedNodeId)")
      }
      else {
        scope.launch(Dispatchers.EDT) {
          LOG.debug { "Node id=$selectedNodeId selected" }
          consoleView.selectNode(node)
        }
      }
    }
  }

  fun onNavigationContextChange(context: BuildTreeNavigationContext) {
    LOG.debug { "Navigation context changed: $context" }
    hasPrevNode = context.hasPrevNode
    hasNextNode = context.hasNextNode
    hasAnyNode = context.hasAnyNode
  }

  fun getNavigationFlow(): Flow<BuildTreeNavigationRequest> {
    return navigationFlow.asSharedFlow()
  }

  fun canNavigate(forward: Boolean): Boolean {
    return if (clearingSelection) forward && hasAnyNode else if (forward) hasNextNode else hasPrevNode
  }

  fun navigate(forward: Boolean) {
    LOG.debug { "Navigate (forward=$forward)" }
    navigationFlow.tryEmit(BuildTreeNavigationRequest(forward))
  }

  var showingSuccessful: Boolean = false
    set(value) {
      if (field != value) {
        field = value
        LOG.debug { "Showing successful set to $value" }
        updateFilteringState()
      }
    }

  var showingWarnings: Boolean = false
    set(value) {
      if (field != value) {
        field = value
        LOG.debug { "Showing warnings set to $value" }
        updateFilteringState()
      }
    }
}

private class BuildTreeEventFlow(scope: CoroutineScope, private val rootNode: ExecutionNode, private val progressRootNode: ExecutionNode) :
  FlowWithHistory<BuildTreeEvent>(scope) {

  private val idGenerator = AtomicInteger(BuildTreeNode.BUILD_PROGRESS_ROOT_ID)
  private val node2Id = mutableMapOf<ExecutionNode, Int>()
  private val nodeStates = mutableMapOf<Int, BuildTreeNode>()
  private val exposeRequests = mutableListOf<BuildTreeExposeRequest>()
  private var filteringState = BuildTreeFilteringState(false, false)
  val id2Node = ConcurrentHashMap<Int, ExecutionNode>()

  override fun getHistory(): List<BuildTreeEvent> = buildList {
    val initialFilteringState = filteringState
    LOG.debug { "Sending initial filtering state: $initialFilteringState" }
    add(initialFilteringState)

    val toSend = nodeStates.values.toList()
    if (toSend.isEmpty()) {
      LOG.debug("No nodes initially available")
    }
    else {
      LOG.debug { "Sending snapshot: ${toSend.map { it.id }}" }
      add(BuildNodesUpdate(System.currentTimeMillis(), toSend))
    }

    exposeRequests.forEach {
      LOG.debug { "Replaying expose request: node id=${it.nodeId}, alsoSelect=${it.alsoSelect}" }
      add(it)
    }
  }

  fun emitNodeUpdates(nodesWithData: Map<ExecutionNode, BuildTreeNode>) = updateHistoryAndEmit {
    nodesWithData.mapNotNull { entry ->
      val node = entry.key
      val parent = node.parent ?: run {
        LOG.warn("No parent for node '$node', skipping update")
        return@mapNotNull null
      }
      val parentId = getNodeId(parent)
      if (parentId == null) {
        LOG.warn("Unknown parent node ('$parent'), skipping update")
        return@mapNotNull null
      }
      val id = getOrCreateNodeId(node)
      val dto = entry.value.copy(id = id, parentId = parentId)
      if (nodeStates[id] == dto) {
        LOG.debug { "State not changed for id=$id, skipping update" }
        null
      }
      else {
        nodeStates[id] = dto
        dto
      }
    }.nullize()?.let { toSend ->
      LOG.debug { "Sending updates: ${toSend.map { it.id }}" }
      BuildNodesUpdate(System.currentTimeMillis(), toSend)
    }
  }

  fun clearNodes() = updateHistoryAndEmit {
    node2Id.clear()
    id2Node.clear()
    nodeStates.clear()
    exposeRequests.clear()
    LOG.debug("Clearing nodes")
    BuildNodesUpdate(System.currentTimeMillis(), emptyList())
  }

  fun expose(node: ExecutionNode?, alsoSelect: Boolean) = updateHistoryAndEmit {
    val nodeId = node?.let { getNodeId(it) }
    if (node != null && nodeId == null) {
      LOG.warn("Unknown node ('$node'), skipping expose")
      null
    }
    else {
      LOG.debug { "Expose node id=$nodeId, alsoSelect=$alsoSelect" }
      val request = BuildTreeExposeRequest(nodeId, alsoSelect)
      if (nodeId != null) { // we want to replay only the requests generated by build events
        exposeRequests.add(request)
      }
      request
    }
  }

  fun updateFilteringState(value: BuildTreeFilteringState) = updateHistoryAndEmit {
    filteringState = value
    value
  }

  private fun getOrCreateNodeId(node: ExecutionNode): Int {
    val existingId = getNodeId(node)
    if (existingId != null) return existingId
    return idGenerator.incrementAndGet().also {
      node2Id[node] = it
      id2Node[it] = node
    }
  }

  private fun getNodeId(node: ExecutionNode): Int? {
    return when (node) {
      rootNode -> BuildTreeNode.ROOT_ID
      progressRootNode -> BuildTreeNode.BUILD_PROGRESS_ROOT_ID
      else -> node2Id[node]
    }
  }
}