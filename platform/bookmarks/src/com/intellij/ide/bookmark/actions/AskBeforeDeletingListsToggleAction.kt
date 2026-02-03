// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.bookmark.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareToggleAction

internal class AskBeforeDeletingListsToggleAction : DumbAwareToggleAction() {

  override fun isSelected(event: AnActionEvent): Boolean = event.bookmarksViewState?.askBeforeDeletingLists ?: true

  override fun setSelected(event: AnActionEvent, isSelected: Boolean) {
    event.bookmarksViewState?.askBeforeDeletingLists = isSelected
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }
}
