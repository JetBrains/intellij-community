// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.openapi.wm.ToolWindowManager.Companion.getInstance
import com.intellij.ui.ExperimentalUI

class MeetNewUiAction : AnAction(), DumbAware {

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = ExperimentalUI.isNewUI() &&
                                         Registry.`is`("ide.experimental.ui.meetNewUi") &&
                                         getToolWindow(e) != null
  }

  override fun actionPerformed(e: AnActionEvent) {
    getToolWindow(e)?.activate(null)
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }

  private fun getToolWindow(e: AnActionEvent): ToolWindow? {
    val project = e.project ?: return null
    return getInstance(project).getToolWindow(ToolWindowId.MEET_NEW_UI)
  }
}
