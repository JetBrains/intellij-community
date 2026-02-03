// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.collaboration.ui.codereview

import com.intellij.collaboration.async.launchNow
import com.intellij.collaboration.async.mapState
import com.intellij.collaboration.async.stateInNow
import com.intellij.collaboration.ui.codereview.details.model.CodeReviewChangeListViewModel
import com.intellij.collaboration.util.RefComparisonChange
import com.intellij.openapi.Disposable
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserNode
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserNodeRenderer
import com.intellij.openapi.vcs.changes.ui.ChangesTree
import com.intellij.ui.ClientProperty
import com.intellij.ui.hover.TreeHoverListener
import com.intellij.ui.render.RenderingHelper
import com.intellij.ui.tree.ui.CustomBoundsTreeUI
import com.intellij.util.asSafely
import com.intellij.util.containers.DisposableWrapperList
import com.intellij.util.containers.TreeTraversal
import com.intellij.util.ui.launchOnShow
import com.intellij.util.ui.tree.TreeUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import java.awt.Point
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.SwingUtilities

@OptIn(FlowPreview::class)
fun ChangesTree.setupCodeReviewProgressModel(vm: CodeReviewChangeListViewModel, model: CodeReviewProgressTreeModel<*>) {
  val tree = this

  TreeHoverListener.DEFAULT.addTo(tree)

  ClientProperty.put(tree, RenderingHelper.SHRINK_LONG_RENDERER, true)
  ClientProperty.put(tree, RenderingHelper.SHRINK_LONG_SELECTION, true)

  val hasViewedState = vm is CodeReviewChangeListViewModel.WithViewedState
  cellRenderer = CodeReviewProgressRenderer(
    hasViewedState,
    renderer = ChangesBrowserNodeRenderer(project, { isShowFlatten }, false),
    codeReviewProgressStateProvider = model::getState
  )
  installViewedStateToggleHandler(vm, model)

  model.addChangeListener {
    repaint()
  }

  tree.launchOnShow("setupCodeReviewProgressModel") {
    // Rebuild the tree if progress model entries are added (or removed in the future)
    // Without a debounce, the previous tree rebuild is likely still happening on first load
    model.isLoading.debounce(100).collect {
      rebuildTree()
      repaint()
    }
  }
}

private fun ChangesTree.installViewedStateToggleHandler(
  vm: CodeReviewChangeListViewModel,
  model: CodeReviewProgressTreeModel<*>,
) {
  val tree = this

  if (vm is CodeReviewChangeListViewModel.WithViewedState) {
    addMouseListener(object : MouseAdapter() {
      override fun mouseClicked(e: MouseEvent) {
        if (!SwingUtilities.isLeftMouseButton(e)) return

        val row = getClosestRowForLocation(1, e.y)
        if (row < 0) return

        val path = getPathForRow(row)
        val cellBounds = tree.ui?.asSafely<CustomBoundsTreeUI>()?.getActualPathBounds(tree, path) ?: return
        val positionInCell = Point(e.x - cellBounds.x, e.y - cellBounds.y)

        val node = path.lastPathComponent as? ChangesBrowserNode<*> ?: return

        // get the top-level rendered cell component
        val component = cellRenderer.getTreeCellRendererComponent(
          tree, node,
          isRowSelected(row),
          isExpanded(row),
          getModel().isLeaf(node),
          row, true)

        if (component !is CodeReviewProgressRendererComponent) return
        val checkboxBounds = component.checkboxBounds(cellBounds.size) ?: return

        if (checkboxBounds.contains(positionInCell)) {
          val change = node.userObject as? RefComparisonChange ?: return

          val state = model.getState(node)
          val isViewed = !state.isRead

          vm.setViewedState(listOf(change), isViewed)
          tree.repaint()
        }
      }
    })
  }
}

/**
 * @param isLoading if true, the state data for the node is still loading.
 */
internal data class NodeCodeReviewProgressState(val isLoading: Boolean, val isRead: Boolean, val discussionsCount: Int)

abstract class CodeReviewProgressTreeModel<T> {
  abstract val isLoading: StateFlow<Boolean>

  private val listeners = DisposableWrapperList<() -> Unit>()

  private val defaultState: NodeCodeReviewProgressState = NodeCodeReviewProgressState(false, true, 0)

  private val stateCache = mutableMapOf<ChangesBrowserNode<*>, NodeCodeReviewProgressState>()

  abstract fun asLeaf(node: ChangesBrowserNode<*>): T?

  abstract fun isLoading(leafValue: T): Boolean

  abstract fun isRead(leafValue: T): Boolean

  abstract fun getUnresolvedDiscussionsCount(leafValue: T): Int

  internal fun getState(node: ChangesBrowserNode<*>): NodeCodeReviewProgressState {
    val cachedState = stateCache[node]
    if (cachedState != null) {
      return cachedState
    }
    // can be rewritten to hand-made bfs not to go down to leafs if state is cached
    val calculatedState = TreeUtil.treeNodeTraverser(node).traverse(TreeTraversal.POST_ORDER_DFS)
      .map {
        val changesNode = it as? ChangesBrowserNode<*> ?: return@map null
        val leafValue = asLeaf(changesNode) ?: return@map null
        getState(leafValue)
      }
      .filterNotNull()
      .fold(defaultState) { acc, state ->
        NodeCodeReviewProgressState(acc.isLoading || state.isLoading, acc.isRead && state.isRead, acc.discussionsCount + state.discussionsCount)
      }
    stateCache[node] = calculatedState
    return calculatedState
  }

  fun addChangeListener(parent: Disposable, listener: () -> Unit) {
    listeners.add(listener, parent)
  }

  fun addChangeListener(listener: () -> Unit) {
    listeners.add(listener)
  }

  private fun getState(leafValue: T): NodeCodeReviewProgressState {
    return NodeCodeReviewProgressState(isLoading(leafValue), isRead(leafValue), getUnresolvedDiscussionsCount(leafValue))
  }

  protected fun fireModelChanged() {
    stateCache.clear()
    listeners.forEach { it() }
  }
}

@OptIn(FlowPreview::class)
class CodeReviewProgressTreeModelFromDetails(cs: CoroutineScope, vm: CodeReviewChangeListViewModel.WithDetails)
  : CodeReviewProgressTreeModel<RefComparisonChange>() {
  private val details = vm.detailsByChange
    .stateInNow(cs, null)

  override val isLoading: StateFlow<Boolean> = details.mapState { it == null }

  init {
    cs.launchNow {
      details.debounce(100).collect {
        fireModelChanged()
      }
    }
  }

  override fun asLeaf(node: ChangesBrowserNode<*>): RefComparisonChange? = node.userObject as? RefComparisonChange

  override fun isLoading(leafValue: RefComparisonChange): Boolean = details.value?.get(leafValue) == null

  override fun isRead(leafValue: RefComparisonChange): Boolean = details.value?.get(leafValue)?.isRead ?: true

  override fun getUnresolvedDiscussionsCount(leafValue: RefComparisonChange): Int = details.value?.get(leafValue)?.discussions ?: 0
}