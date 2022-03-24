// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.bookmark.actions

import com.intellij.ide.bookmark.BookmarksManagerImpl
import com.intellij.ide.bookmark.ui.tree.BookmarkNode
import com.intellij.ide.bookmark.ui.tree.GroupNode
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.ui.UIBundle.messagePointer
import com.intellij.util.ui.tree.TreeUtil
import java.util.function.Supplier

internal class NodeMoveUpAction : NodeMoveAction(false, messagePointer("move.up.action.name"))
internal class NodeMoveDownAction : NodeMoveAction(true, messagePointer("move.down.action.name"))
internal abstract class NodeMoveAction(val next: Boolean, dynamicText: Supplier<String>) : DumbAwareAction(dynamicText) {

  override fun update(event: AnActionEvent) = with(event.presentation) {
    isEnabledAndVisible = process(event, false)
    if (!isVisible) isVisible = !ActionPlaces.isPopupPlace(event.place)
  }

  override fun actionPerformed(event: AnActionEvent) {
    process(event, true)
  }

  private fun process(event: AnActionEvent, perform: Boolean): Boolean {
    val manager = event.bookmarksManager as? BookmarksManagerImpl ?: return false
    val tree = event.bookmarksViewFromToolWindow?.tree ?: return false
    val path = TreeUtil.getSelectedPathIfOne(tree)
    val node1 = TreeUtil.getAbstractTreeNode(path)
    val node2 = TreeUtil.getAbstractTreeNode(when {
      next -> TreeUtil.nextVisibleSibling(tree, path)
      else -> TreeUtil.previousVisibleSibling(tree, path)
    })
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

internal class NodeMoveActionPromoter : ActionPromoter {
  override fun suppress(actions: List<AnAction>, context: DataContext) = when {
    context.getData(PlatformDataKeys.TOOL_WINDOW)?.id != ToolWindowId.BOOKMARKS -> null
    actions.none { it is NodeMoveAction } -> null
    else -> actions.filter { it !is NodeMoveAction }
  }
}
