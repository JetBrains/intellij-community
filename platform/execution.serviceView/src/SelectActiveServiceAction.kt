// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.execution.serviceView

import com.intellij.execution.services.ServiceViewManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification

internal class SelectActiveServiceAction : ToggleAction(), DumbAware, ActionRemoteBehaviorSpecification.Frontend {

  override fun getActionUpdateThread(): ActionUpdateThread =  ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    super.update(e)
    val toolWindow = e.getData(PlatformDataKeys.TOOL_WINDOW)
    val content = toolWindow?.getContentManager()?.getSelectedContent()
    e.presentation.setEnabledAndVisible(content != null && content.getComponent() is ServiceView)
  }

  override fun isSelected(e: AnActionEvent): Boolean {
    val project = e.project
    return if (project == null) true else (ServiceViewManager.getInstance(project) as ServiceViewManagerImpl).isSelectActiveService
  }

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    val project = e.project
    if (project == null) return
    (ServiceViewManager.getInstance(project) as ServiceViewManagerImpl).isSelectActiveService = state
  }
}