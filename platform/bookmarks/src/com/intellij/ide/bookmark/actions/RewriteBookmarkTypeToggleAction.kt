// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.bookmark.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareToggleAction

internal class RewriteBookmarkTypeToggleAction : DumbAwareToggleAction() {

  override fun isSelected(event: AnActionEvent): Boolean = event.bookmarksViewState?.rewriteBookmarkType?.not() ?: true

  override fun setSelected(event: AnActionEvent, isSelected: Boolean) {
    event.bookmarksViewState?.rewriteBookmarkType = !isSelected
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }
}
