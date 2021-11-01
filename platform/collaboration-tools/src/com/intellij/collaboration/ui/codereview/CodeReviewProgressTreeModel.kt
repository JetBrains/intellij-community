// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.collaboration.ui.codereview

import com.intellij.openapi.Disposable
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserNode
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserNodeRenderer
import com.intellij.openapi.vcs.changes.ui.ChangesTree
import com.intellij.util.containers.DisposableWrapperList

fun ChangesTree.setupCodeReviewProgressModel(parent: Disposable, model: CodeReviewProgressTreeModel<*>) {
  val nodeRenderer = ChangesBrowserNodeRenderer(project, { isShowFlatten }, false)
  cellRenderer = CodeReviewProgressRenderer(nodeRenderer, model::isRead, model::getUnresolvedDiscussionsCount)

  model.addChangeListener(parent) { repaint() }
}

abstract class CodeReviewProgressTreeModel<T> {
  private val listeners = DisposableWrapperList<() -> Unit>()

  abstract fun asLeaf(node: ChangesBrowserNode<*>): T?

  abstract fun isRead(leafValue: T): Boolean

  abstract fun getUnresolvedDiscussionsCount(leafValue: T): Int

  fun isRead(node: ChangesBrowserNode<*>): Boolean {
    val leafValue = asLeaf(node) ?: return true
    return isRead(leafValue)
  }

  fun getUnresolvedDiscussionsCount(node: ChangesBrowserNode<*>): Int {
    val leafValue = asLeaf(node) ?: return 0
    return getUnresolvedDiscussionsCount(leafValue)
  }

  fun addChangeListener(parent: Disposable, listener: () -> Unit) {
    listeners.add(listener, parent)
  }

  protected fun fireModelChanged() = listeners.forEach { it() }
}