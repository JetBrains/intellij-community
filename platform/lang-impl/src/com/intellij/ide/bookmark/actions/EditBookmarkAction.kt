// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.bookmark.actions

import com.intellij.ide.bookmark.BookmarkBundle
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.Messages

internal class EditBookmarkAction : DumbAwareAction(BookmarkBundle.messagePointer("bookmark.edit.action.text")) {

  override fun update(event: AnActionEvent) {
    val manager = event.bookmarksManager
    val component = event.getData(PlatformDataKeys.CONTEXT_COMPONENT)
    val bookmark = component?.let { event.contextBookmark }
    val description = bookmark?.let { manager?.defaultGroup?.getDescription(it) }
    event.presentation.isEnabledAndVisible = description != null
  }

  override fun actionPerformed(event: AnActionEvent) {
    val manager = event.bookmarksManager ?: return
    val component = event.getData(PlatformDataKeys.CONTEXT_COMPONENT) ?: return
    val bookmark = event.contextBookmark ?: return
    val group = manager.defaultGroup ?: return
    val description = group.getDescription(bookmark) ?: return
    Messages.showInputDialog(component,
      BookmarkBundle.message("action.bookmark.edit.description.dialog.message"),
      BookmarkBundle.message("action.bookmark.edit.description.dialog.title"),
      null,
      description,
      null
    )?.let { group.setDescription(bookmark, { it }) }
  }

  init {
    isEnabledInModalContext = true
  }
}
