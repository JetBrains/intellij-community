// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.bookmark.ui.tree

import com.intellij.ide.bookmark.BookmarkBundle
import com.intellij.ide.bookmark.BookmarksListProvider
import com.intellij.ide.bookmark.ui.GroupRenameDialog
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.Nls
import javax.swing.JComponent

internal class GroupListProvider(private val project: Project) : BookmarksListProvider {
  override fun getWeight(): Int = Int.MAX_VALUE
  override fun getProject(): Project = project

  override fun createNode(): AbstractTreeNode<*>? = null

  override fun getEditActionText(): @Nls String = BookmarkBundle.message("bookmark.group.rename.action.text")
  override fun canEdit(selection: Any): Boolean = selection is GroupNode
  override fun performEdit(selection: Any, parent: JComponent) {
    val node = selection as? GroupNode ?: return
    val group = node.value ?: return
    val manager = node.bookmarksManager ?: return
    GroupRenameDialog(project, parent, manager, group).showAndGetGroup()
  }

  override fun getDeleteActionText(): @Nls String = BookmarkBundle.message("bookmark.group.delete.action.text")
  override fun canDelete(selection: List<*>): Boolean = selection.all { it is GroupNode }
  override fun performDelete(selection: List<*>, parent: JComponent): Unit = selection.forEach {
    val node = it as? GroupNode
    node?.value?.remove()
  }
}
