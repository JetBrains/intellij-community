// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.build

import com.intellij.ide.rpc.navigatable
import com.intellij.ide.rpc.weakRpcId
import com.intellij.ide.ui.icons.icon
import com.intellij.ide.ui.icons.rpcId
import com.intellij.openapi.application.EDT
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.ui.split.SplitComponentModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

private val LOG = fileLogger()

@ApiStatus.Internal
class BuildTreeViewModel(private val consoleView: BuildTreeConsoleView, private val scope: CoroutineScope) : SplitComponentModel {
  // sequential execution ensures consistent snapshot construction
  private val sequentialDispatcher = Dispatchers.Default.limitedParallelism(1)
  // buffering is important to ensure proper ordering between events emission and 'onSubscription' execution
  private val nodesFlow = MutableSharedFlow<BuildTreeEvent>(extraBufferCapacity = Int.MAX_VALUE)

  private val idGenerator = AtomicInteger(BuildTreeNode.BUILD_PROGRESS_ROOT_ID)
  private val node2Id = mutableMapOf<ExecutionNode, Int>() // accessed only via sequentialDispatcher
  private val nodeStates = mutableMapOf<Int, BuildTreeNode>() // accessed only via sequentialDispatcher
  private val id2Node = ConcurrentHashMap<Int, ExecutionNode>()

  private val navigationFlow = MutableSharedFlow<BuildTreeNavigationRequest>(extraBufferCapacity = Int.MAX_VALUE)
  private val filteringStateFlow = MutableStateFlow(BuildTreeFilteringState(false, true))
  private val isDisposed = MutableStateFlow(false)

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

  override val providerId: String = "BuildTree"

  fun getTreeEventsFlow(): Flow<BuildTreeEvent> {
    return flow {
      nodesFlow.onSubscription {
        val toSend = nodeStates.values.toList()
        if (toSend.isEmpty()) {
          LOG.debug("No nodes initially available")
        }
        else {
          LOG.debug { "Sending snapshot: ${toSend.map { it.id }}" }
          emit(BuildNodesUpdate(System.currentTimeMillis(), toSend))
        }
      }.collect(this)
    }.flowOn(sequentialDispatcher)
  }

  fun createOrUpdateNodes(nodes: Collection<ExecutionNode>) {
    assert(consoleView.isCorrectThread) // it should be ok to extract information from the passed nodes here
    if (nodes.isEmpty()) return
    val nodesWithData = nodes.associateWith { it.toDto() }
    scope.launch(sequentialDispatcher) {
      val toSend = nodesWithData.mapNotNull { entry ->
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
        if (nodeStates[id]?.sameAs(dto) == true) {
          LOG.debug { "State not changed for id=$id, skipping update" }
          null
        }
        else {
          nodeStates[id] = dto
          dto
        }
      }
      if (toSend.isNotEmpty()) {
        LOG.debug { "Sending updates: ${toSend.map { it.id }}" }
        nodesFlow.emit(BuildNodesUpdate(System.currentTimeMillis(), toSend))
      }
    }
  }

  fun clearNodes() {
    scope.launch(sequentialDispatcher) {
      node2Id.clear()
      id2Node.clear()
      nodeStates.clear()
      LOG.debug("Clearing nodes")
      nodesFlow.emit(BuildNodesUpdate(System.currentTimeMillis(), emptyList()))
    }
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
    scope.launch(sequentialDispatcher) {
      val nodeId = node?.let { getNodeId(it) }
      if (node != null && nodeId == null) {
        LOG.warn("Unknown node ('$node'), skipping expose")
      }
      else {
        LOG.debug { "Expose node id=$nodeId, alsoSelect=$alsoSelect" }
        nodesFlow.emit(BuildTreeExposeRequest(nodeId, alsoSelect))
      }
    }
  }

  // to be called only via sequentialDispatcher
  private fun getOrCreateNodeId(node: ExecutionNode): Int {
    val existingId = getNodeId(node)
    if (existingId != null) return existingId
    return idGenerator.incrementAndGet().also {
      node2Id[node] = it
      id2Node[it] = node
    }
  }

  // to be called only via sequentialDispatcher
  private fun getNodeId(node: ExecutionNode): Int? {
    return when(node) {
      consoleView.rootElement -> BuildTreeNode.ROOT_ID
      consoleView.buildProgressRootNode -> BuildTreeNode.BUILD_PROGRESS_ROOT_ID
      else -> node2Id[node]
    }
  }

  fun getNodeById(id: Int): ExecutionNode? {
    return when(id) {
      BuildTreeNode.ROOT_ID -> consoleView.rootElement
      BuildTreeNode.BUILD_PROGRESS_ROOT_ID -> consoleView.buildProgressRootNode
      else -> id2Node[id]
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

  fun getFilteringStateFlow(): Flow<BuildTreeFilteringState> {
    return filteringStateFlow.asStateFlow()
  }

  fun getShutdownStateFlow(): Flow<Boolean> {
    return isDisposed.asStateFlow()
  }

  fun canNavigate(forward: Boolean): Boolean {
    return if (clearingSelection) forward && hasAnyNode else if (forward) hasNextNode else hasPrevNode
  }

  fun navigate(forward: Boolean) {
    LOG.debug { "Navigate (forward=$forward)" }
    navigationFlow.tryEmit(BuildTreeNavigationRequest(forward))
  }

  var showingSuccessful: Boolean
    get() = filteringStateFlow.value.showSuccessful
    set(value) {
      LOG.debug { "Showing successful set to $value" }
      filteringStateFlow.value = filteringStateFlow.value.copy(showSuccessful = value)
    }

  var showingWarnings: Boolean
    get() = filteringStateFlow.value.showWarnings
    set(value) {
      LOG.debug { "Showing warnings set to $value" }
      filteringStateFlow.value = filteringStateFlow.value.copy(showWarnings = value)
    }

   override fun dispose() {
    isDisposed.value = true
  }
}

// difference from 'equals' is in handling of icon and navigatable references
private fun BuildTreeNode.sameAs(other: BuildTreeNode): Boolean {
  return id == other.id &&
         parentId == other.parentId &&
         title == other.title &&
         name == other.name &&
         hint == other.hint &&
         duration == other.duration &&
         autoExpand == other.autoExpand &&
         hasProblems == other.hasProblems &&
         visibleAlways == other.visibleAlways &&
         visibleAsSuccessful == other.visibleAsSuccessful &&
         visibleAsWarning == other.visibleAsWarning &&
         icon?.icon() === other.icon?.icon() &&
         navigatables.size == other.navigatables.size &&
         navigatables.indices.all { navigatables[it].navigatable() === other.navigatables[it].navigatable() }
}