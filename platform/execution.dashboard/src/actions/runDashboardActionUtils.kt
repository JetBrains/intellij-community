// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.execution.dashboard.actions

import com.intellij.execution.dashboard.RunDashboardServiceId
import com.intellij.execution.services.ServiceViewActionUtils
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.platform.execution.dashboard.RunDashboardCoroutineScopeProvider
import com.intellij.platform.execution.dashboard.splitApi.RunDashboardServiceRpc
import com.intellij.platform.execution.dashboard.splitApi.frontend.FrontendRunDashboardService
import com.intellij.platform.execution.dashboard.splitApi.frontend.tree.FrontendRunConfigurationNode
import com.intellij.platform.project.projectId
import kotlinx.coroutines.launch

internal fun getSelectedNode(e: AnActionEvent): FrontendRunDashboardService? {
  return getSelectedNodes(e).singleOrNull()
}

internal fun getSelectedNodes(e: AnActionEvent): List<FrontendRunDashboardService> {
  return ServiceViewActionUtils.getTargets(e, FrontendRunConfigurationNode::class.java).mapNotNull { it.value }
}

internal fun scheduleEditConfiguration(serviceId: RunDashboardServiceId, project: Project) {
  RunDashboardCoroutineScopeProvider.getInstance(project).cs.launch {
    RunDashboardServiceRpc.getInstance().editConfiguration(project.projectId(), serviceId)
  }
}

internal fun scheduleCopyConfiguration(serviceId: RunDashboardServiceId, project: Project) {
  RunDashboardCoroutineScopeProvider.getInstance(project).cs.launch {
    RunDashboardServiceRpc.getInstance().copyConfiguration(project.projectId(), serviceId)
  }
}

internal fun scheduleUpdateRunConfigurationFolderNames(serviceIds: List<RunDashboardServiceId>, newGroupName: String?, project: Project) {
  RunDashboardCoroutineScopeProvider.getInstance(project).cs.launch {
    RunDashboardServiceRpc.getInstance().updateConfigurationFolderName(serviceIds, newGroupName, project.projectId())
  }
}
