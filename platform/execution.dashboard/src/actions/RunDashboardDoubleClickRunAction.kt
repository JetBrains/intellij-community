// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.execution.dashboard.actions

import com.intellij.execution.dashboard.RunDashboardManager
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.project.DumbAware

private const val DOUBLE_CLICK_SETTING = "run.dashboard.double.click.run"

class RunDashboardDoubleClickRunAction : ToggleAction(), DumbAware {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    super.update(e)
    val project = e.project
    if (project == null) {
      e.presentation.isEnabledAndVisible = false
      return
    }
    val toolWindow = e.getData(PlatformDataKeys.TOOL_WINDOW)
    if (toolWindow == null) {
      e.presentation.isEnabledAndVisible = false
      return
    }
    e.presentation.isEnabledAndVisible =
      RunDashboardManager.getInstance(project).toolWindowId == toolWindow.id
  }

  override fun isSelected(e: AnActionEvent): Boolean {
    return isDoubleClickRunEnabled()
  }

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    PropertiesComponent.getInstance().setValue(DOUBLE_CLICK_SETTING, state, true)
  }

  companion object {
    @JvmStatic
    internal fun isDoubleClickRunEnabled(): Boolean {
      return PropertiesComponent.getInstance().getBoolean(DOUBLE_CLICK_SETTING, true)
    }
  }
}