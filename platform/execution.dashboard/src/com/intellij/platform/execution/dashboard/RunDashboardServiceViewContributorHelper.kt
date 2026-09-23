// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.execution.dashboard

import com.intellij.execution.RunContentDescriptorId
import com.intellij.execution.RunContentDescriptorIdImpl
import com.intellij.execution.dashboard.RunDashboardGroup
import com.intellij.execution.services.ServiceViewDnDDescriptor
import com.intellij.openapi.project.Project
import com.intellij.platform.execution.dashboard.splitApi.RunDashboardManagerRpc
import com.intellij.platform.execution.dashboard.splitApi.frontend.FrontendRunDashboardService
import com.intellij.platform.execution.dashboard.splitApi.frontend.tree.FrontendRunConfigurationNode
import com.intellij.platform.project.projectId
import kotlinx.coroutines.launch

internal object RunDashboardServiceViewContributorHelper {
  @JvmStatic
  fun scheduleReorderConfigurations(project: Project, target: FrontendRunConfigurationNode, drop: FrontendRunConfigurationNode, position: ServiceViewDnDDescriptor.Position) {
    RunDashboardCoroutineScopeProvider.getInstance(project).cs.launch {
      RunDashboardManagerRpc.getInstance().reorderConfigurations(project.projectId(),
                                                                 target.service.uuid,
                                                                 drop.service.uuid,
                                                                 position.name)
    }
  }

  @JvmStatic
  fun scheduleNavigateToService(project: Project, service: FrontendRunDashboardService, requestFocus: Boolean) {
    RunDashboardCoroutineScopeProvider.getInstance(project).cs.launch {
      RunDashboardManagerRpc.getInstance().tryNavigate(project.projectId(),
                                                       service.runDashboardServiceDto.uuid,
                                                       requestFocus)
    }
  }

  @JvmStatic
  fun scheduleRemoveService(project: Project, service: FrontendRunDashboardService) {
    RunDashboardCoroutineScopeProvider.getInstance(project).cs.launch {
      RunDashboardManagerRpc.getInstance().removeService(project.projectId(),
                                                         service.runDashboardServiceDto.uuid)
    }
  }

  @JvmStatic
  fun scheduleRerunConfiguration(project: Project, service: FrontendRunDashboardService) {
    RunDashboardCoroutineScopeProvider.getInstance(project).cs.launch {
      RunDashboardManagerRpc.getInstance().rerunConfiguration(project.projectId(), service.runDashboardServiceDto.uuid)
    }
  }

  @JvmStatic
  fun scheduleRemoveFolderGroup(project: Project, groupName: String) {
    RunDashboardCoroutineScopeProvider.getInstance(project).cs.launch {
      RunDashboardManagerRpc.getInstance().removeFolderGroup(project.projectId(), groupName)
    }
  }

  @JvmStatic
  fun scheduleDropRunConfigurationNodeOnFolderNode(project: Project, drop: FrontendRunDashboardService, target: RunDashboardGroup) {
    RunDashboardCoroutineScopeProvider.getInstance(project).cs.launch {
      RunDashboardManagerRpc.getInstance().addServiceToFolder(project.projectId(), drop.runDashboardServiceDto.uuid, target.name)
    }
  }

  @JvmStatic
  fun scheduleNodeLinkNavigation(project: Project, link: String, node: FrontendRunConfigurationNode) {
    RunDashboardCoroutineScopeProvider.getInstance(project).cs.launch {
      RunDashboardManagerRpc.getInstance().tryNavigateLink(project.projectId(),
                                                           link,
                                                           node.service.uuid)
    }
  }

  @JvmStatic
  fun scheduleAttachRunContentDescriptorId(
    project: Project,
    oldDescriptorId: RunContentDescriptorId?, newDescriptorId: RunContentDescriptorId,
  ) {
    // cast because the execution module does not allow using serialization library, and the id interface must be there (it is used in api methods declared there as well)
    oldDescriptorId as RunContentDescriptorIdImpl?
    newDescriptorId as RunContentDescriptorIdImpl
    RunDashboardCoroutineScopeProvider.getInstance(project).cs.launch {
      RunDashboardManagerRpc.getInstance().attachRunContentDescriptorId(project.projectId(),
                                                                        oldDescriptorId, newDescriptorId)
    }
  }

  @JvmStatic
  fun scheduleDetachRunContentDescriptorId(project: Project, descriptorId: RunContentDescriptorId) {
    descriptorId as RunContentDescriptorIdImpl
    RunDashboardCoroutineScopeProvider.getInstance(project).cs.launch {
      RunDashboardManagerRpc.getInstance().detachRunContentDescriptorId(project.projectId(), descriptorId)
    }
  }

  @JvmStatic
  fun scheduleSetConfigurationTypes(project: Project, configurationTypes: Set<String>) {
    RunDashboardCoroutineScopeProvider.getInstance(project).cs.launch {
      RunDashboardManagerRpc.getInstance().setConfigurationTypes(project.projectId(),
                                                                 configurationTypes)
    }
  }
}