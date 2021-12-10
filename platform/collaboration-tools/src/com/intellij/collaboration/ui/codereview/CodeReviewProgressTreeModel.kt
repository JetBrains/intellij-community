// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.collaboration.ui.codereview

import com.intellij.openapi.Disposable
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserNode
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserNodeRenderer
import com.intellij.openapi.vcs.changes.ui.ChangesTree
import com.intellij.util.containers.DisposableWrapperList
import com.intellij.util.containers.TreeTraversal
import com.intellij.util.ui.tree.TreeUtil

fun ChangesTree.setupCodeReviewProgressModel(parent: Disposable, model: CodeReviewProgressTreeModel<*>) {
  val nodeRenderer = ChangesBrowserNodeRenderer(project, { isShowFlatten }, false)
  cellRenderer = CodeReviewProgressRenderer(nodeRenderer, model::getState)

  model.addChangeListener(parent) { repaint() }
}

internal data class NodeCodeReviewProgressState(val isRead: Boolean, val discussionsCount: Int)

abstract class CodeReviewProgressTreeModel<T> {
  private val listeners = DisposableWrapperList<() -> Unit>()

  private val defaultState: NodeCodeReviewProgressState = NodeCodeReviewProgressState(true, 0)

  private val stateCache = mutableMapOf<ChangesBrowserNode<*>, NodeCodeReviewProgressState>()

  abstract fun asLeaf(node: ChangesBrowserNode<*>): T?

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
        NodeCodeReviewProgressState(acc.isRead && state.isRead, acc.discussionsCount + state.discussionsCount)
      }
    stateCache[node] = calculatedState
    return calculatedState
  }

  fun addChangeListener(parent: Disposable, listener: () -> Unit) {
    listeners.add(listener, parent)
  }

  private fun getState(leafValue: T): NodeCodeReviewProgressState {
    return NodeCodeReviewProgressState(isRead(leafValue), getUnresolvedDiscussionsCount(leafValue))
  }

  protected fun fireModelChanged() {
    stateCache.clear()
    listeners.forEach { it() }
  }
}