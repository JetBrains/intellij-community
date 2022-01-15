// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.bookmark.actions

import com.intellij.ide.bookmark.BookmarkBundle.messagePointer
import com.intellij.ide.bookmark.ui.GroupCreateDialog
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction

internal class GroupCreateAction : DumbAwareAction(messagePointer("bookmark.group.create.action.text")) {

  override fun update(event: AnActionEvent) {
    val view = event.bookmarksManager?.let { event.bookmarksView }
    event.presentation.isEnabled = view != null
  }

  override fun actionPerformed(event: AnActionEvent) {
    val manager = event.bookmarksManager ?: return
    val view = event.bookmarksView ?: return
    GroupCreateDialog(event.project, view, manager).showAndGetGroup(false)
  }

  init {
    isEnabledInModalContext = true
  }
}
