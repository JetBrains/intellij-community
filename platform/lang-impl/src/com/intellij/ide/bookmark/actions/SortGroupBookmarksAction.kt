// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.bookmark.actions

import com.intellij.ide.bookmark.BookmarkBundle.messagePointer
import com.intellij.ide.bookmark.BookmarksManagerImpl
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction

internal class SortGroupBookmarksAction : DumbAwareAction(messagePointer("sort.group.bookmarks.action.text")) {

  override fun update(event: AnActionEvent) {
    val manager = event.bookmarksManager as? BookmarksManagerImpl
    val node = manager?.let { event.selectedGroupNode }
    val empty = node?.value?.getBookmarks().isNullOrEmpty()
    event.presentation.isEnabledAndVisible = !empty
  }

  override fun actionPerformed(event: AnActionEvent) {
    val manager = event.bookmarksManager as? BookmarksManagerImpl ?: return
    val node = event.selectedGroupNode ?: return
    manager.sort(node.value)
  }
}
