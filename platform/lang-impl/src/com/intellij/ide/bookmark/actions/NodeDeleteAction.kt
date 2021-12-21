// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.bookmark.actions

import com.intellij.CommonBundle.messagePointer
import com.intellij.ide.bookmark.BookmarksListProviderService
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction

internal class NodeDeleteAction : DumbAwareAction(messagePointer("button.delete")) {

  override fun update(event: AnActionEvent) {
    event.presentation.isEnabledAndVisible = false
    val project = event.project ?: return
    val nodes = event.bookmarksView?.selectedNodes ?: return
    val provider = BookmarksListProviderService.findProvider(project) { it.canDelete(nodes) } ?: return
    provider.deleteActionText?.let { event.presentation.text = it }
    event.presentation.isEnabledAndVisible = true
  }

  override fun actionPerformed(event: AnActionEvent) {
    val project = event.project ?: return
    val view = event.bookmarksView ?: return
    val nodes = view.selectedNodes ?: return
    BookmarksListProviderService.findProvider(project) { it.canDelete(nodes) }?.performDelete(nodes, view.tree)
  }

  init {
    isEnabledInModalContext = true
  }
}
