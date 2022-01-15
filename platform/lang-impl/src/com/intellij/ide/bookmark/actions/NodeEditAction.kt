// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.bookmark.actions

import com.intellij.CommonBundle.messagePointer
import com.intellij.ide.bookmark.BookmarksListProvider
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction

internal class NodeEditAction : DumbAwareAction(messagePointer("button.edit")) {

  override fun update(event: AnActionEvent) {
    event.presentation.isEnabled = false
    val project = event.project ?: return
    val node = event.bookmarksView?.selectedNode ?: return
    val provider = BookmarksListProvider.EP.findFirstSafe(project) { it.canEdit(node) } ?: return
    provider.editActionText?.let { event.presentation.text = it }
    event.presentation.isEnabled = true
  }

  override fun actionPerformed(event: AnActionEvent) {
    val project = event.project ?: return
    val view = event.bookmarksView ?: return
    val node = view.selectedNode ?: return
    BookmarksListProvider.EP.findFirstSafe(project) { it.canEdit(node) }?.performEdit(node, view.tree)
  }

  init {
    isEnabledInModalContext = true
  }
}
