// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.bookmarks.actions

import com.intellij.ide.bookmark.BookmarkType
import com.intellij.ide.bookmarks.BookmarkBundle.message
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction

internal class DeleteBookmarkTypeAction : DumbAwareAction() {
  override fun update(event: AnActionEvent) {
    val type = event.dataContext.context?.bookmark?.type ?: BookmarkType.DEFAULT
    event.presentation.isEnabledAndVisible = type != BookmarkType.DEFAULT
    event.presentation.text = message("mnemonic.chooser.mnemonic.delete.action.text")
  }

  override fun actionPerformed(event: AnActionEvent) {
    val context = event.dataContext.context ?: return
    val bookmark = context.bookmark ?: return
    context.manager.deleteMnemonic(bookmark)
  }

  init {
    isEnabledInModalContext = true
  }
}
