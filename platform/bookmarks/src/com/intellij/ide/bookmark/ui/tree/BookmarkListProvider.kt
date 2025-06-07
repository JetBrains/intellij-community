// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.bookmark.ui.tree

import com.intellij.ide.bookmark.*
import com.intellij.ide.bookmark.providers.FileBookmarkImpl
import com.intellij.ide.bookmark.providers.LineBookmarkImpl
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.idea.ActionsBundle
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import org.jetbrains.annotations.Nls
import javax.swing.JComponent

internal class BookmarkListProvider(private val project: Project) : BookmarksListProvider {
  override fun getWeight(): Int = Int.MAX_VALUE
  override fun getProject(): Project = project

  override fun createNode(): AbstractTreeNode<*>? = null
  override fun getDescriptor(node: AbstractTreeNode<*>): OpenFileDescriptor? = when (val value = node.equalityObject) {
    is LineBookmarkImpl -> value.descriptor
    is FileBookmarkImpl -> value.descriptor
    is LineBookmark -> OpenFileDescriptor(project, value.file, value.line)
    is FileBookmark -> OpenFileDescriptor(project, value.file)
    else -> null
  }

  override fun getEditActionText(): @Nls String? = ActionsBundle.message("action.EditBookmark.text")
  override fun canEdit(selection: Any): Boolean = selection is BookmarkNode<*>
  override fun performEdit(selection: Any, parent: JComponent) {
    val node = selection as? BookmarkNode<*> ?: return
    val bookmark = node.value ?: return
    val group = node.bookmarkGroup ?: return
    val description = group.getDescription(bookmark)
    Messages.showInputDialog(parent,
                             BookmarkBundle.message("action.bookmark.edit.description.dialog.message"),
                             BookmarkBundle.message("action.bookmark.edit.description.dialog.title"),
                             null,
                             description,
                             null
    )?.let {
      if (description != null) group.setDescription(bookmark, it)
      else group.add(bookmark, BookmarkType.DEFAULT, it)
    }
  }

  override fun getDeleteActionText(): @Nls String = BookmarkBundle.message("bookmark.delete.action.text")
  override fun canDelete(selection: List<*>): Boolean = selection.all { it is BookmarkNode<*> }
  override fun performDelete(selection: List<*>, parent: JComponent): Unit = selection.forEach { performDelete(it) }
  private fun performDelete(node: Any?) {
    if (node is FileNode) node.children.forEach { performDelete(it) }
    if (node is BookmarkNode<*>) node.value?.let { node.bookmarkGroup?.remove(it) }
  }
}
