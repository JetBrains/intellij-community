// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.bookmark.actions

import com.intellij.ide.bookmark.BookmarkBundle
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction

internal class ToggleDefaultGroupAction : DumbAwareAction() {

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(event: AnActionEvent) {
    val isDefault = event.selectedGroupNode?.value?.isDefault
    event.presentation.isEnabledAndVisible = isDefault != null
    if (isDefault == true) {
      event.presentation.setText(BookmarkBundle.messagePointer("default.bookmark.group.unmark.action.text"))
    }
  }

  override fun actionPerformed(event: AnActionEvent) {
    event.selectedGroupNode?.value?.apply { isDefault = !isDefault }
  }
}
