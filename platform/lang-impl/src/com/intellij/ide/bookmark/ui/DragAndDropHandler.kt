// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.bookmark.ui

import com.intellij.ide.bookmark.*
import com.intellij.ide.dnd.*
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
      if (bookmarks.size == nodes.size) return DnDDragStartBean(AttachedBookmarks(bookmarks))
      if (bookmarks.isNotEmpty()) return null
      val groups = nodes.mapNotNull { it.value as? BookmarkGroup }
      if (groups.size == nodes.size) return DnDDragStartBean(AttachedBookmarkGroups(groups))
      if (groups.isNotEmpty()) return null
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
