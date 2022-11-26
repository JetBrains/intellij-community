// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.bookmark.actions

import com.intellij.CommonBundle.messagePointer
import com.intellij.ide.bookmark.BookmarkBundle
import com.intellij.ide.bookmark.BookmarkGroup
import com.intellij.ide.bookmark.BookmarksListProviderService
import com.intellij.ide.bookmark.FileBookmark
import com.intellij.ide.bookmark.ui.BookmarksViewState
import com.intellij.ide.bookmark.ui.tree.FileNode
import com.intellij.ide.bookmark.ui.tree.GroupNode
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.DoNotAskOption
import com.intellij.openapi.ui.MessageDialogBuilder

internal class NodeDeleteAction : DumbAwareAction(messagePointer("button.delete")) {

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(event: AnActionEvent) {
    event.presentation.isEnabledAndVisible = false
    val project = event.project ?: return
    val nodes = event.bookmarkNodes ?: return
    val provider = BookmarksListProviderService.findProvider(project) { it.canDelete(nodes) } ?: return
    provider.deleteActionText?.let { event.presentation.text = it }
    event.presentation.isEnabledAndVisible = true
  }

  override fun actionPerformed(event: AnActionEvent) {
    val project = event.project ?: return
    val view = event.bookmarksView ?: return
    val nodes = event.bookmarkNodes ?: return
    val bookmarksViewState = event.bookmarksViewState
    val shouldDelete = when {
      bookmarksViewState == null -> true
      nodes.any { it is GroupNode || it is FileNode } -> {
        if (!askBeforeDeleting(bookmarksViewState, nodes)) true
        else {
          val fileNode = nodes.any { it is FileNode }
          val title = when {
            fileNode && nodes.size == 1 -> BookmarkBundle.message("dialog.message.delete.single.node.title")
            fileNode -> BookmarkBundle.message("dialog.message.delete.multiple.nodes.title")
            nodes.size == 1 -> BookmarkBundle.message("dialog.message.delete.single.list.title")
            else -> BookmarkBundle.message("dialog.message.delete.multiple.lists.title")
          }
          val message = when {
            fileNode && nodes.size == 1 -> BookmarkBundle.message("dialog.message.delete.single.node",
                                                                  nodes.first().children.size,
                                                                  (nodes.first().value as FileBookmark).file.name)
            fileNode -> BookmarkBundle.message("dialog.message.delete.multiple.nodes")
            nodes.size == 1 -> BookmarkBundle.message("dialog.message.delete.single.list", (nodes.first { it is GroupNode }.value as BookmarkGroup).name)
            else -> BookmarkBundle.message("dialog.message.delete.multiple.lists")
          }
          MessageDialogBuilder
            .yesNo(title, message)
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

  private fun askBeforeDeleting(bookmarksViewState: BookmarksViewState, nodes: List<AbstractTreeNode<*>>): Boolean {
    if (!bookmarksViewState.askBeforeDeletingLists || nodes.isEmpty()) return false
    if (nodes.size > 1) return true
    val firstNode = nodes.first()
    return when (firstNode.children.size) {
      0 -> false
      1 -> firstNode.children.first().children.size > 1
      else -> true
    }
  }

  init {
    isEnabledInModalContext = true
  }
}
