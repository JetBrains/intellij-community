// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.execution.dashboard

import com.intellij.execution.Executor
import com.intellij.execution.RunContentDescriptorId
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.dashboard.RunDashboardManagerProxy
import com.intellij.execution.dashboard.RunDashboardUiManager
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.execution.ui.RunContentManagerExtension
import com.intellij.openapi.project.Project
import com.intellij.ui.content.Content
import com.intellij.ui.content.ContentManager
import java.util.function.BiPredicate
import javax.swing.Icon

internal class RunDashboardRunContentManagerExtension : RunContentManagerExtension {
  override fun isShownInServices(project: Project, configuration: RunConfiguration): Boolean {
    return RunDashboardManagerProxy.getInstance(project).isShowInDashboard(configuration)
  }

  override fun isSupported(project: Project, executor: Executor): Boolean {
    return RunDashboardUiManager.getInstance(project).isSupported(executor)
  }

  override fun getToolWindowId(project: Project): String {
    return RunDashboardUiManager.getInstance(project).toolWindowId
  }

  override fun getToolWindowIcon(project: Project): Icon {
    return RunDashboardUiManager.getInstance(project).toolWindowIcon
  }

  override fun getContentManager(project: Project): ContentManager {
    return RunDashboardUiManager.getInstance(project).dashboardContentManager
  }

  override fun getToolWindowIdIfCreated(project: Project): String? {
    return RunDashboardUiManager.getInstanceIfCreated(project)?.toolWindowId
  }

  override fun getContentManagerIfCreated(project: Project): ContentManager? {
    return RunDashboardUiManager.getInstanceIfCreated(project)?.dashboardContentManager
  }

  override fun getReuseCondition(project: Project): BiPredicate<in Content, RunConfiguration?> {
    return RunDashboardUiManager.getInstance(project).reuseCondition
  }

  override fun contentReused(project: Project, content: Content, oldDescriptor: RunContentDescriptor) {
    RunDashboardUiManager.getInstance(project).contentReused(content, oldDescriptor)
  }

  override fun navigateToRunContent(project: Project, descriptorId: RunContentDescriptorId, focus: Boolean?) {
    RunDashboardUiManager.getInstance(project).navigateToServiceOnRun(descriptorId, focus)
  }

  override fun updateRunContent(project: Project, withStructure: Boolean) {
    RunDashboardManagerProxy.getInstance(project).updateDashboard(withStructure)
  }

  override fun findService(project: Project, descriptorId: RunContentDescriptorId): Any? {
    return RunDashboardManagerProxy.getInstance(project).findService(descriptorId)
  }

  override fun getConfiguredRunConfigurationTypes(project: Project): Set<String> {
    return RunDashboardManagerProxy.getInstance(project).types
  }

  override fun setConfiguredRunConfigurationTypes(project: Project, types: Set<String>) {
    RunDashboardManagerProxy.getInstance(project).types = types
  }
}
