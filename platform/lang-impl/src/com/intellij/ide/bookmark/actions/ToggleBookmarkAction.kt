// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.bookmark.actions

import com.intellij.icons.AllIcons.Actions.Checked
import com.intellij.ide.bookmark.BookmarkBundle
import com.intellij.ide.bookmark.BookmarkType
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Toggleable
import com.intellij.openapi.editor.EditorGutter
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.util.SystemInfo
import java.awt.event.MouseEvent
import javax.swing.SwingUtilities

internal class ToggleBookmarkAction : Toggleable, DumbAwareAction(BookmarkBundle.messagePointer("bookmark.toggle.action.text")) {

  override fun update(event: AnActionEvent) {
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
    val manager = event.bookmarksManager ?: return
    val bookmark = event.contextBookmark ?: return
    val type = manager.getType(bookmark) ?: BookmarkType.DEFAULT
    manager.toggle(bookmark, type)
  }

  private val MouseEvent.isUnexpected // see MouseEvent.isUnexpected in LineBookmarkProvider
    get() = !SwingUtilities.isLeftMouseButton(this) || isPopupTrigger || if (SystemInfo.isMac) !isMetaDown else !isControlDown

  init {
    isEnabledInModalContext = true
  }
}
