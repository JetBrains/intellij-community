// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.execution.serviceView

import com.intellij.execution.ExecutionBundle
import com.intellij.execution.services.ServiceViewManager
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.wm.ToolWindowId

internal class ConfigureServicesAction : DumbAwareAction(), ActionRemoteBehaviorSpecification.Frontend {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    val toolWindow = e.getData(PlatformDataKeys.TOOL_WINDOW)
    if (toolWindow != null && toolWindow.id != ToolWindowId.SERVICES) {
      e.presentation.text = ExecutionBundle.message("service.view.include.into.services.action.text")
      e.presentation.icon = AllIcons.ToolbarDecorator.Import
    }
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return

    val toolWindow = e.getData(PlatformDataKeys.TOOL_WINDOW)
    if (toolWindow != null && toolWindow.id != ToolWindowId.SERVICES) {
      (ServiceViewManager.getInstance(project) as ServiceViewManagerImpl).includeToolWindow(toolWindow.id)
    }
    else {
      ConfigureServicesDialog(project).show()
    }
  }
}