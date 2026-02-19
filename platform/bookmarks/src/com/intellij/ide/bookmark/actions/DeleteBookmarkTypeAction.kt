// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.bookmark.actions

import com.intellij.ide.bookmark.BookmarkType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction

internal class DeleteBookmarkTypeAction : DumbAwareAction() {

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(event: AnActionEvent) {
    if (checkMultipleSelectionAndDisableAction(event)) return
    val manager = event.bookmarksManager
    val bookmark = event.contextBookmark
    val type = bookmark?.let { manager?.getType(it) }
    event.presentation.isEnabledAndVisible = type != null && type != BookmarkType.DEFAULT
  }

  override fun actionPerformed(event: AnActionEvent) {
    val manager = event.bookmarksManager ?: return
    val bookmark = event.contextBookmark ?: return
    manager.setType(bookmark, BookmarkType.DEFAULT)
  }

  init {
    isEnabledInModalContext = true
  }
}
