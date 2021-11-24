// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.bookmark.actions

import com.intellij.ide.bookmark.BookmarkBundle
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.Messages

internal class EditBookmarkAction : DumbAwareAction(BookmarkBundle.messagePointer("bookmark.edit.action.text")) {

  override fun update(event: AnActionEvent) = with(event.presentation) {
    isEnabledAndVisible = process(event, false) != null
  }

  override fun actionPerformed(event: AnActionEvent) {
    process(event, true)
  }

  private fun process(event: AnActionEvent, perform: Boolean): String? {
    val manager = event.bookmarksManager ?: return null
    val component = event.getData(PlatformDataKeys.CONTEXT_COMPONENT) ?: return null
    val bookmark = event.contextBookmark ?: return null
    val group = manager.getGroups(bookmark).firstOrNull() ?: return null
    val description = group.getDescription(bookmark) ?: return null
    return if (!perform) description
    else Messages.showInputDialog(component,
      BookmarkBundle.message("action.bookmark.edit.description.dialog.message"),
      BookmarkBundle.message("action.bookmark.edit.description.dialog.title"),
      null,
      description,
      null
    )?.also { group.setDescription(bookmark, it) }
  }

  init {
    isEnabledInModalContext = true
  }
}
