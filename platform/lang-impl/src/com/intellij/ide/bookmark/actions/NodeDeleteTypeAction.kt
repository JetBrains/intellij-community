// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.bookmark.actions

import com.intellij.ide.bookmark.Bookmark
import com.intellij.ide.bookmark.BookmarkBundle
import com.intellij.ide.bookmark.BookmarkType
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction

internal class NodeDeleteTypeAction : DumbAwareAction(BookmarkBundle.messagePointer("mnemonic.chooser.mnemonic.delete.action.text")) {

  override fun update(event: AnActionEvent) {
    event.presentation.isEnabledAndVisible = false
    val manager = event.bookmarksManager ?: return
    val nodes = event.bookmarksView?.selectedNodes ?: return
    for (node in nodes) {
      val bookmark = node.value as? Bookmark ?: return
      val type = manager.getType(bookmark) ?: return
      if (type == BookmarkType.DEFAULT) return
    }
    event.presentation.isEnabledAndVisible = true
  }

  override fun actionPerformed(event: AnActionEvent) {
    val manager = event.bookmarksManager ?: return
    val nodes = event.bookmarksView?.selectedNodes ?: return
    for (node in nodes) {
      val bookmark = node.value as? Bookmark ?: continue
      manager.setType(bookmark, BookmarkType.DEFAULT)
    }
  }

  init {
    isEnabledInModalContext = true
  }
}
