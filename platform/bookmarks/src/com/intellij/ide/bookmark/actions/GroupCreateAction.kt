// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.bookmark.actions

import com.intellij.ide.bookmark.ui.GroupCreateDialog
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction

internal class GroupCreateAction : DumbAwareAction() {

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

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
