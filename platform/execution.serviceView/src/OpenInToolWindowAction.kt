// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.execution.serviceView

import com.intellij.execution.ExecutionBundle
import com.intellij.execution.services.ServiceViewContributor
import com.intellij.execution.services.ServiceViewManager
import com.intellij.execution.services.ServiceViewToolWindowDescriptor
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowId

class OpenInToolWindowAction : DumbAwareAction(), ActionRemoteBehaviorSpecification.Frontend {
  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }

  override fun update(e: AnActionEvent) {
    val toolWindow = e.getData(PlatformDataKeys.TOOL_WINDOW)
    if (toolWindow != null && toolWindow.id != ToolWindowId.SERVICES) {
      e.presentation.text = ExecutionBundle.message("service.view.include.into.services.action.text")
    }

    val project = e.project
    val contributor = getContributor(e)
    val enabled = project != null && contributor != null && isExclusionAllowed(contributor, project)

    e.presentation.isEnabled = enabled
    e.presentation.isVisible = enabled || !e.isFromContextMenu
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return

    val toolWindow = e.getData(PlatformDataKeys.TOOL_WINDOW)
    if (toolWindow != null && toolWindow.id != ToolWindowId.SERVICES) {
      (ServiceViewManager.getInstance(project) as ServiceViewManagerImpl).includeToolWindow(toolWindow.id)
    }
    else {
      val contributor = getContributor(e) ?: return
      val excluded = ArrayList(ConfigureServicesDialog.collectServices(project).second)
      excluded.add(contributor)
      (ServiceViewManager.getInstance(project) as ServiceViewManagerImpl).setExcludedContributors(excluded)
    }
  }

  private fun isExclusionAllowed(contributor: ServiceViewContributor<*>, project: Project): Boolean {
    return (contributor.getViewDescriptor(project) as? ServiceViewToolWindowDescriptor)?.isExclusionAllowed != false
  }

  private fun getContributor(e: AnActionEvent): ServiceViewContributor<*>? {
    return ServiceViewActionProvider.getSelectedItems(e).singleOrNull()?.rootContributor
  }
}