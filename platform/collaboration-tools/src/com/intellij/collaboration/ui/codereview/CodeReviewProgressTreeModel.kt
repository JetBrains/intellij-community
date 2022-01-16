// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.collaboration.ui.codereview

import com.intellij.openapi.Disposable
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserNode
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserNodeRenderer
import com.intellij.openapi.vcs.changes.ui.ChangesTree
import com.intellij.util.containers.DisposableWrapperList

fun ChangesTree.setupCodeReviewProgressModel(parent: Disposable, model: CodeReviewProgressTreeModel) {
  val nodeRenderer = ChangesBrowserNodeRenderer(project, { isShowFlatten }, false)
  cellRenderer = CodeReviewProgressRenderer(nodeRenderer, model::isRead, model::getUnresolvedDiscussionsCount)

  model.addChangeListener(parent) { repaint() }
}

abstract class CodeReviewProgressTreeModel {
  private val listeners = DisposableWrapperList<() -> Unit>()

  abstract fun isRead(node: ChangesBrowserNode<*>): Boolean

  abstract fun getUnresolvedDiscussionsCount(node: ChangesBrowserNode<*>): Int

  fun addChangeListener(parent: Disposable, listener: () -> Unit) {
    listeners.add(listener, parent)
  }

  protected fun fireModelChanged() = listeners.forEach { it() }
}