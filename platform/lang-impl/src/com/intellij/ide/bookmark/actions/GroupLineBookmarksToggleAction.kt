// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.bookmark.actions

import com.intellij.ide.bookmark.BookmarkBundle.messagePointer
import com.intellij.openapi.actionSystem.ToggleOptionAction
import com.intellij.openapi.project.DumbAware

internal class GroupLineBookmarksToggleAction : DumbAware, ToggleOptionAction({ it.bookmarksViewFromToolWindow?.groupLineBookmarks }) {
  init {
    templatePresentation.setText(messagePointer("view.group.line.bookmarks.action.text"))
  }
}
