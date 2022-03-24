// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.bookmark.ui.tree

import com.intellij.ide.bookmark.BookmarkBundle.message
import com.intellij.ide.bookmark.BookmarksListProvider
import com.intellij.ide.bookmark.ui.GroupRenameDialog
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.project.Project
import javax.swing.JComponent

internal class GroupListProvider(private val project: Project) : BookmarksListProvider {
  override fun getWeight() = Int.MAX_VALUE
  override fun getProject() = project

  override fun createNode(): AbstractTreeNode<*>? = null

  override fun getEditActionText() = message("bookmark.group.rename.action.text")
  override fun canEdit(selection: Any) = selection is GroupNode
  override fun performEdit(selection: Any, parent: JComponent) {
    val node = selection as? GroupNode ?: return
    val group = node.value ?: return
    val manager = node.bookmarksManager ?: return
    GroupRenameDialog(project, parent, manager, group).showAndGetGroup()
  }

  override fun getDeleteActionText() = message("bookmark.group.delete.action.text")
  override fun canDelete(selection: List<*>) = selection.all { it is GroupNode }
  override fun performDelete(selection: List<*>, parent: JComponent) = selection.forEach {
    val node = it as? GroupNode
    node?.value?.remove()
  }
}
