// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui.experimental.meetNewUi

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.ExperimentalUI

private class MeetNewUIAction : AnAction(), DumbAware {
  override fun actionPerformed(e: AnActionEvent) {
    getToolWindow(e)?.activate(null)
  }

  override fun update(e: AnActionEvent) {
    if (ExperimentalUI.isNewUI() && Registry.`is`("ide.experimental.ui.meetNewUi")) {
      val toolWindow = getToolWindow(e)
      if (toolWindow == null) {
        e.presentation.isEnabledAndVisible = false
      }
      else {
        e.presentation.isEnabledAndVisible = true
        e.presentation.icon = if (SystemInfoRt.isMac) null else toolWindow.icon
      }
    }
    else {
      e.presentation.isEnabledAndVisible = false
    }
  }

  override fun getActionUpdateThread() = ActionUpdateThread.EDT

  private fun getToolWindow(e: AnActionEvent): ToolWindow? {
    val project = getEventProject(e) ?: return null
    return ToolWindowManager.getInstance(project).getToolWindow(ToolWindowId.MEET_NEW_UI)
  }
}
