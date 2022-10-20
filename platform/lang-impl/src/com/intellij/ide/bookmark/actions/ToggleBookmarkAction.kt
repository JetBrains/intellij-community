// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.bookmark.actions

import com.intellij.icons.AllIcons.Actions.Checked
import com.intellij.ide.bookmark.Bookmark
import com.intellij.ide.bookmark.BookmarkBundle
import com.intellij.ide.bookmark.BookmarkType
import com.intellij.ide.bookmark.ui.GroupSelectDialog
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.editor.EditorGutter
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.util.SystemInfo
import java.awt.event.MouseEvent
import javax.swing.SwingUtilities

fun checkMultipleSelectionAndDisableAction(event: AnActionEvent): Boolean {
  if (event.contextBookmarks != null) {
    event.presentation.isEnabledAndVisible = false
    return true
  }
  return false
}

internal class ToggleBookmarkAction : Toggleable, DumbAwareAction(BookmarkBundle.messagePointer("bookmark.toggle.action.text")) {

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(event: AnActionEvent) {
    if (event.contextBookmarks != null) {
      event.presentation.apply {
        isEnabledAndVisible = true
        text = BookmarkBundle.message("bookmark.add.multiple.action.text")
      }
      return
    }

    val manager = event.bookmarksManager
    val bookmark = event.contextBookmark
    val type = bookmark?.let { manager?.getType(it) }
    event.presentation.apply {
      isEnabledAndVisible = bookmark != null
      icon = when (event.place) {
        ActionPlaces.TOUCHBAR_GENERAL -> {
          Toggleable.setSelected(this, type != null)
          Checked
        }
        else -> null
      }
      text = when {
        !ActionPlaces.isPopupPlace(event.place) -> BookmarkBundle.message("bookmark.toggle.action.text")
        type != null -> BookmarkBundle.message("bookmark.delete.action.text")
        else -> BookmarkBundle.message("bookmark.add.action.text")
      }
    }
  }

  override fun actionPerformed(event: AnActionEvent) {
    val unexpectedGutterClick = (event.inputEvent as? MouseEvent)?.run { source is EditorGutter && isUnexpected }
    if (unexpectedGutterClick == true) return
    val bookmarks = event.contextBookmarks
    when {
      bookmarks != null -> addMultipleBookmarks(event, bookmarks)
      else -> addSingleBookmark(event)
    }
  }

  private fun addSingleBookmark(event: AnActionEvent) {
    val manager = event.bookmarksManager ?: return
    val bookmark = event.contextBookmark ?: return
    val type = manager.getType(bookmark) ?: BookmarkType.DEFAULT
    manager.toggle(bookmark, type)

    val selectedText = event.getData(CommonDataKeys.EDITOR)?.selectionModel?.selectedText
    if (!selectedText.isNullOrBlank()) {
      manager.getGroups(bookmark).forEach { group -> group.setDescription(bookmark, selectedText) }
    }
  }

  private fun addMultipleBookmarks(event: AnActionEvent, bookmarks: List<Bookmark>) {
    val manager = event.bookmarksManager ?: return
    val group = GroupSelectDialog(event.project, null, manager, manager.groups)
                  .showAndGetGroup(false)
                ?: return
    bookmarks.forEach { group.add(it, BookmarkType.DEFAULT) }
  }

  private val MouseEvent.isUnexpected // see MouseEvent.isUnexpected in LineBookmarkProvider
    get() = !SwingUtilities.isLeftMouseButton(this) || isPopupTrigger || if (SystemInfo.isMac) !isMetaDown else !isControlDown

  init {
    isEnabledInModalContext = true
  }
}
