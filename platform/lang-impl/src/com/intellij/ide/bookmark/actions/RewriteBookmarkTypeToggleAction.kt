// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.bookmark.actions

import com.intellij.ide.bookmark.BookmarkBundle.messagePointer
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareToggleAction

internal class RewriteBookmarkTypeToggleAction : DumbAwareToggleAction(messagePointer("view.confirm.bookmark.type.action.text")) {

  override fun isSelected(event: AnActionEvent) = event.bookmarksViewState?.rewriteBookmarkType?.not() ?: true

  override fun setSelected(event: AnActionEvent, isSelected: Boolean) {
    event.bookmarksViewState?.rewriteBookmarkType = !isSelected
  }
}
