// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.bookmark.actions

import com.intellij.ide.bookmark.BookmarkBundle
import com.intellij.ide.bookmark.LineBookmark
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction

internal class AddAnotherBookmarkAction : DumbAwareAction(BookmarkBundle.messagePointer("bookmark.add.another.action.text")) {

  override fun update(event: AnActionEvent) {
    event.presentation.isEnabledAndVisible = process(event, false)
  }

  override fun actionPerformed(event: AnActionEvent) {
    process(event, true)
  }

  private fun process(event: AnActionEvent, perform: Boolean): Boolean {
    val manager = event.bookmarksManager ?: return false
    val bookmark = event.contextBookmark ?: return false
    if (bookmark is LineBookmark) return false
    val type = manager.getType(bookmark) ?: return false
    if (perform) manager.add(bookmark, type)
    return true
  }

  init {
    isEnabledInModalContext = true
  }
}
