// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.bookmark.ui.tree

import com.intellij.ide.bookmark.BookmarkBundle.message
import com.intellij.ide.bookmark.BookmarkType
import com.intellij.ide.bookmark.BookmarksListProvider
import com.intellij.ide.bookmark.FileBookmark
import com.intellij.ide.bookmark.LineBookmark
import com.intellij.ide.bookmark.providers.FileBookmarkImpl
import com.intellij.ide.bookmark.providers.LineBookmarkImpl
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import javax.swing.JComponent

internal class BookmarkListProvider(private val project: Project) : BookmarksListProvider {
  override fun getWeight() = Int.MAX_VALUE
  override fun getProject() = project

  override fun createNode(): AbstractTreeNode<*>? = null
  override fun getDescriptor(node: AbstractTreeNode<*>) = when (val value = node.equalityObject) {
    is LineBookmarkImpl -> value.descriptor
    is FileBookmarkImpl -> value.descriptor
    is LineBookmark -> OpenFileDescriptor(project, value.file, value.line)
    is FileBookmark -> OpenFileDescriptor(project, value.file)
    else -> null
  }

  override fun getEditActionText() = message("bookmark.edit.action.text")
  override fun canEdit(selection: Any) = selection is BookmarkNode<*>
  override fun performEdit(selection: Any, parent: JComponent) {
    val node = selection as? BookmarkNode<*> ?: return
    val bookmark = node.value ?: return
    val group = node.bookmarkGroup ?: return
    val description = group.getDescription(bookmark)
    Messages.showInputDialog(parent,
      message("action.bookmark.edit.description.dialog.message"),
      message("action.bookmark.edit.description.dialog.title"),
      null,
      description,
      null
    )?.let {
      if (description != null) group.setDescription(bookmark, it)
      else group.add(bookmark, BookmarkType.DEFAULT, it)
    }
  }

  override fun getDeleteActionText() = message("bookmark.delete.action.text")
  override fun canDelete(selection: List<*>) = selection.all { it is BookmarkNode<*> }
  override fun performDelete(selection: List<*>, parent: JComponent) = selection.forEach { performDelete(it) }
  private fun performDelete(node: Any?) {
    if (node is FileNode) node.children.forEach { performDelete(it) }
    if (node is BookmarkNode<*>) node.value?.let { node.bookmarkGroup?.remove(it) }
  }
}
