// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.bookmark.actions

import com.intellij.ide.bookmark.BookmarkBundle.messagePointer
import com.intellij.ide.bookmark.BookmarksManagerImpl
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction

internal class SortGroupsAndBookmarksAction : DumbAwareAction(messagePointer("sort.groups.and.bookmarks.action.text")) {

  override fun update(event: AnActionEvent) {
    val manager = event.bookmarksManager as? BookmarksManagerImpl
    event.presentation.isEnabledAndVisible = manager != null
  }

  override fun actionPerformed(event: AnActionEvent) {
    val manager = event.bookmarksManager as? BookmarksManagerImpl ?: return
    manager.sort()
  }
}
