// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.bookmark.ui.tree

import com.intellij.icons.AllIcons
import com.intellij.ide.bookmark.Bookmark
import com.intellij.ide.bookmark.BookmarkBundle.message
import com.intellij.ide.bookmark.BookmarkGroup
import com.intellij.ide.bookmark.FileBookmark
import com.intellij.ide.bookmark.LineBookmark
import com.intellij.ide.bookmark.providers.LineBookmarkProvider
import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.ide.util.treeView.AbstractTreeNodeCache
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.tree.LeafState.ASYNC
import com.intellij.util.containers.ContainerUtil.addIfNotNull

internal class GroupNode(project: Project, group: BookmarkGroup) : AbstractTreeNode<BookmarkGroup>(project, group) {
  private val cache = AbstractTreeNodeCache<Bookmark, AbstractTreeNode<*>>(this) { it.createNode() }

  override fun getChildren(): List<AbstractTreeNode<*>> {
    var bookmarks = value.getBookmarks()

    // shows line bookmarks only in the popup
    val view = parentRootNode?.value
    if (view?.isPopup == true) {
      bookmarks = bookmarks.filterIsInstance<LineBookmark>()
    }

    // retrieve provider to group line bookmarks within corresponding file bookmarks
    val provider = view?.run { if (groupLineBookmarks.isSelected) LineBookmarkProvider.find(project) else null }
    if (provider != null) {
      val map = mutableMapOf<VirtualFile, FileBookmark?>()
      bookmarks.forEach {
        when (it) {
          is LineBookmark -> map.putIfAbsent(it.file, null)
          is FileBookmark -> map[it.file] = it
        }
      }
      val set = mutableSetOf<Bookmark>()
      val list = mutableListOf<Bookmark>()
      bookmarks.forEach {
        if (it is FileBookmark) {
          val file = it.file
          if (map.contains(file)) {
            val old = map.remove(file)
            addIfNotNull(set, old)
            addIfNotNull(list, old ?: provider.createBookmark(file))
          }
        }
        if (!set.contains(it)) list.add(it)
      }
      bookmarks = list
    }

    // reuse cached nodes
    val nodes = cache.getNodes(bookmarks).onEach {
      if (it is BookmarkNode) it.bookmarkGroup = value
      if (it is FileNode) it.removeChildren()
    }
    if (provider == null) return nodes

    // group line bookmarks within corresponding file bookmarks
    val map = mutableMapOf<VirtualFile, FileNode>()
    nodes.forEach { if (it is FileNode) map.putIfAbsent(it.value.file, it) }
    return nodes.mapNotNull {
      val bookmark = it.value as? LineBookmark ?: return@mapNotNull it
      val node = map[bookmark.file] ?: return@mapNotNull it
      node.addChild(it)
      null
    }
  }

  override fun update(presentation: PresentationData) {
    presentation.setIcon(AllIcons.Nodes.BookmarkGroup)
    if (value.isDefault) {
      presentation.addText("${value.name}  ", SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES)
      presentation.addText(message("default.group.marker"), SimpleTextAttributes.GRAYED_ATTRIBUTES)
    }
    else {
      presentation.presentableText = value.name
    }
  }
}
