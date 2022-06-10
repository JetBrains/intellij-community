// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.bookmark.actions

import com.intellij.CommonBundle.messagePointer
import com.intellij.ide.bookmark.BookmarkBundle
import com.intellij.ide.bookmark.BookmarkGroup
import com.intellij.ide.bookmark.BookmarksListProviderService
import com.intellij.ide.bookmark.ui.tree.GroupNode
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.DoNotAskOption
import com.intellij.openapi.ui.MessageDialogBuilder
import com.intellij.ui.UIBundle

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
    val bookmarksViewState = event.bookmarksViewState
    val shouldDelete = when {
      bookmarksViewState == null -> true
      nodes.any { it is GroupNode } -> {
        if (!bookmarksViewState.askBeforeDeletingLists) true
        else {
          val message =
            if (nodes.size == 1) BookmarkBundle.message("dialog.message.delete.single.bookmark.list", (nodes.first { it is GroupNode }.value as BookmarkGroup).name)
            else BookmarkBundle.message("dialog.message.delete.multiple.bookmark.lists")
          MessageDialogBuilder
            .yesNo(BookmarkBundle.message("dialog.message.delete.title"), message)
            .yesText(BookmarkBundle.message("dialog.message.delete.button"))
            .noText(BookmarkBundle.message("dialog.message.cancel.button"))
            .doNotAsk(object : DoNotAskOption.Adapter() {
              override fun rememberChoice(isSelected: Boolean, exitCode: Int) {
                bookmarksViewState.askBeforeDeletingLists = !isSelected
              }
            })
            .asWarning()
            .ask(project)
        }
      }
      else -> true
    }

    if (shouldDelete) {
        BookmarksListProviderService.findProvider(project) { it.canDelete(nodes) }?.performDelete(nodes, view.tree)
    }
  }

  init {
    isEnabledInModalContext = true
  }
}
