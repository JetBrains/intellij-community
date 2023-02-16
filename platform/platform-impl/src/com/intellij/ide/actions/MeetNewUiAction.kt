// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions

import com.intellij.idea.ActionsBundle
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.wm.ToolWindowManager.Companion.getInstance
import com.intellij.openapi.wm.impl.ToolWindowManagerImpl
import com.intellij.ui.ExperimentalUI

private val TOOLWINDOW_ID = ActionsBundle.message("action.MeetNewUi.text")

class MeetNewUiAction : AnAction(), DumbAware {

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = ExperimentalUI.isNewUI() &&
                                         getToolWindowManager(e)?.isToolWindowRegistered(TOOLWINDOW_ID) == true
  }

  override fun actionPerformed(e: AnActionEvent) {
    getToolWindowManager(e)?.activateToolWindow(TOOLWINDOW_ID, null, true)
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }

  private fun getToolWindowManager(e: AnActionEvent): ToolWindowManagerImpl? {
    val project = e.project ?: return null
    return getInstance(project) as ToolWindowManagerImpl
  }
}
