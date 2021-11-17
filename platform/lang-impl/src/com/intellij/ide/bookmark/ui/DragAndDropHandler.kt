// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.bookmark.ui

import com.intellij.ide.bookmark.*
import com.intellij.ide.bookmark.providers.FileBookmarkImpl
import com.intellij.ide.bookmark.providers.LineBookmarkImpl
import com.intellij.ide.dnd.*
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.awt.RelativeRectangle
import com.intellij.util.ui.tree.TreeUtil
import java.awt.Rectangle

internal class DragAndDropHandler(val view: BookmarksView) : DnDTargetChecker, DnDDropHandler.WithResult {

  private class AttachedBookmarks(val occurrences: List<BookmarkOccurrence>)
  private class AttachedBookmarkGroups(val groups: List<BookmarkGroup>)

  fun createBean(info: DnDActionInfo): DnDDragStartBean? {
    val nodes = view.selectedNodes ?: return null
    if (info.isMove) {
      val bookmarks = nodes.mapNotNull { it.bookmarkOccurrence }
      if (bookmarks.isNotEmpty()) {
        if (bookmarks.size != nodes.size) return null // not only bookmarks are selected
        if (view.groupLineBookmarks.isSelected) {
          val count = bookmarks.count { it.bookmark is LineBookmarkImpl }
          if (count > 0) {
            val set = mutableSetOf<VirtualFile>()
            bookmarks.forEach { if (it.bookmark is LineBookmarkImpl) set.add(it.bookmark.file) }
            if (count < bookmarks.size) {
              bookmarks.forEach { if (it.bookmark is FileBookmarkImpl) set.remove(it.bookmark.file) }
              if (set.size != 0) return null // do not drag line bookmarks without corresponding file bookmark
              return DnDDragStartBean(AttachedBookmarks(bookmarks.filter { it.bookmark !is LineBookmarkImpl }))
            }
            if (set.size != 1) return null // do not drag line bookmarks with different file grouping
          }
        }
        return DnDDragStartBean(AttachedBookmarks(bookmarks))
      }
      val groups = nodes.mapNotNull { it.value as? BookmarkGroup }
      if (groups.isNotEmpty()) {
        if (groups.size != nodes.size) return null // not only groups are selected
        return DnDDragStartBean(AttachedBookmarkGroups(groups))
      }
    }
    return null
  }

  override fun update(event: DnDEvent): Boolean {
    val possible = tryDrop(event, true)
    if (!possible) event.hideHighlighter()
    event.isDropPossible = possible
    return true
  }

  override fun tryDrop(event: DnDEvent) = tryDrop(event, false)
  private fun tryDrop(event: DnDEvent, updateOnly: Boolean): Boolean {
    val point = event.point ?: return false
    if (event.handlerComponent != view.tree || !view.tree.isShowing) return false
    val path = view.tree.getClosestPathForLocation(point.x, point.y) ?: return false
    val node = TreeUtil.getAbstractTreeNode(path) ?: return false
    val bounds = view.tree.getPathBounds(path) ?: return false
    val strict = bounds.y <= point.y && point.y < bounds.maxY
    val above = point.y < bounds.centerY
    when (val attached = event.attachedObject) {
      is AttachedBookmarks -> {
        val manager = BookmarksManager.getInstance(view.project) as? BookmarksManagerImpl ?: return false
        val group = node.value as? BookmarkGroup
        if (group != null && strict) return when {
          !updateOnly -> manager.dragInto(group, attached.occurrences)
          !manager.canDragInto(group, attached.occurrences) -> false
          else -> setHighlighting(event, bounds)
        }
        val occurrence = node.bookmarkOccurrence ?: return false
        return when {
          !updateOnly -> manager.drag(above, occurrence, attached.occurrences)
          !manager.canDrag(above, occurrence, attached.occurrences) -> false
          else -> setLineHighlighting(event, bounds, above)
        }
      }
      is AttachedBookmarkGroups -> {
        val manager = BookmarksManager.getInstance(view.project) as? BookmarksManagerImpl ?: return false
        val group = node.value as? BookmarkGroup ?: return false
        return when {
          !updateOnly -> manager.drag(above, group, attached.groups)
          !manager.canDrag(above, group, attached.groups) -> false
          else -> setLineHighlighting(event, bounds, above)
        }
      }
    }
    return false
  }

  private fun setHighlighting(event: DnDEvent, bounds: Rectangle): Boolean {
    event.setHighlighting(RelativeRectangle(view.tree, bounds), DnDEvent.DropTargetHighlightingType.RECTANGLE)
    return true
  }

  private fun setLineHighlighting(event: DnDEvent, bounds: Rectangle, above: Boolean): Boolean {
    if (!above) bounds.y += bounds.height
    bounds.y -= 1
    bounds.height = 2
    event.setHighlighting(RelativeRectangle(view.tree, bounds), DnDEvent.DropTargetHighlightingType.FILLED_RECTANGLE)
    return true
  }
}
