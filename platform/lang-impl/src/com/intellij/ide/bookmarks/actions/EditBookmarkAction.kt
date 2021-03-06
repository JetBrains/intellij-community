// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.bookmarks.actions

import com.intellij.icons.AllIcons.Actions.Edit
import com.intellij.ide.bookmarks.BookmarkBundle.messagePointer
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.project.DumbAwareAction

internal class EditBookmarkAction : DumbAwareAction(
  messagePointer("bookmark.edit.action.text"),
  messagePointer("bookmark.edit.action.description"),
  Edit) {

  override fun update(event: AnActionEvent) {
    val component = event.getData(PlatformDataKeys.CONTEXT_COMPONENT)
    val bookmark = component?.let { event.dataContext.context?.bookmark }
    event.presentation.isEnabledAndVisible = bookmark != null
  }

  override fun actionPerformed(event: AnActionEvent) {
    val component = event.getData(PlatformDataKeys.CONTEXT_COMPONENT) ?: return
    val context = event.dataContext.context ?: return
    val bookmark = context.bookmark ?: return
    context.manager.editDescription(bookmark, component)
  }

  init {
    isEnabledInModalContext = true
  }
}
