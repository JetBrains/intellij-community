// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.bookmark.actions

import com.intellij.ide.bookmark.BookmarksManagerImpl
import com.intellij.ide.bookmark.ui.tree.BookmarkNode
import com.intellij.ide.bookmark.ui.tree.GroupNode
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.util.ui.tree.TreeUtil

internal class NodeMoveUpAction : NodeMoveAction(false)

internal class NodeMoveDownAction : NodeMoveAction(true)

internal abstract class NodeMoveAction(val next: Boolean) : DumbAwareAction() {

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

  override fun update(event: AnActionEvent): Unit = with(event.presentation) {
    isEnabled = process(event, next, false)
    isVisible = isEnabled ||
                !event.isFromContextMenu ||
                process(event, !next, false)
  }

  override fun actionPerformed(event: AnActionEvent) {
    process(event, next, true)
  }

  companion object {
    private fun process(event: AnActionEvent, next: Boolean, perform: Boolean): Boolean {
      val manager = event.bookmarksManager as? BookmarksManagerImpl ?: return false
      val tree = event.bookmarksView?.tree ?: return false
      val path1 = TreeUtil.getSelectedPathIfOne(tree)
      val path2 = when {
        next -> TreeUtil.nextVisibleSibling(tree, path1)
        else -> TreeUtil.previousVisibleSibling(tree, path1)
      }
      val node1 = TreeUtil.getAbstractTreeNode(path1)
      val node2 = TreeUtil.getAbstractTreeNode(path2)
      return when {
        node1 is GroupNode && node2 is GroupNode -> {
          val group1 = node1.value ?: return false
          val group2 = node2.value ?: return false
          if (group1.isDefault || group2.isDefault) return false
          if (perform) manager.move(group1, group2)
          true
        }
        node1 is BookmarkNode && node2 is BookmarkNode -> {
          val group = node1.bookmarkGroup ?: return false
          if (group != node2.bookmarkGroup) return false
          val bookmark1 = node1.value ?: return false
          val bookmark2 = node2.value ?: return false
          if (perform) manager.move(group, bookmark1, bookmark2)
          true
        }
        else -> false
      }
    }
  }
}

internal class NodeMoveActionPromoter : ActionPromoter {
  override fun suppress(actions: List<AnAction>, context: DataContext): List<AnAction>? = when {
    context.getData(PlatformDataKeys.TOOL_WINDOW)?.id != ToolWindowId.BOOKMARKS -> null
    actions.none { it is NodeMoveAction } -> null
    else -> actions.filter { it !is NodeMoveAction }
  }
}
