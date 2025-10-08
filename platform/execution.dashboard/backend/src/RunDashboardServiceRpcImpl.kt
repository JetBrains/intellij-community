// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.execution.dashboard.backend

import com.intellij.execution.dashboard.RunDashboardServiceId
import com.intellij.execution.impl.RunManagerImpl
import com.intellij.ide.rpc.ComponentDirectTransferId
import com.intellij.openapi.application.EDT
import com.intellij.platform.execution.dashboard.BackendLuxedRunDashboardContentManager
import com.intellij.platform.execution.dashboard.RunDashboardManagerImpl
import com.intellij.platform.execution.dashboard.splitApi.*
import com.intellij.platform.project.ProjectId
import com.intellij.platform.project.findProjectOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.withContext

internal class RunDashboardServiceRpcImpl : RunDashboardServiceRpc {
  override suspend fun getSettings(projectId: ProjectId): Flow<RunDashboardSettingsDto> {
    val project = projectId.findProjectOrNull() ?: return emptyFlow()
    return RunDashboardManagerImpl.getInstance(project).settingsDto
  }

  override suspend fun getServices(projectId: ProjectId): Flow<List<RunDashboardServiceDto>> {
    val project = projectId.findProjectOrNull() ?: return emptyFlow()
    return RunDashboardManagerImpl.getInstance(project).servicesDto
  }

  override suspend fun getStatuses(projectId: ProjectId): Flow<ServiceStatusDto> {
    val project = projectId.findProjectOrNull() ?: return emptyFlow()
    return RunDashboardManagerImpl.getInstance(project).statusesDto
  }

  override suspend fun getCustomizations(projectId: ProjectId): Flow<ServiceCustomizationDto> {
    val project = projectId.findProjectOrNull() ?: return emptyFlow()
    return RunDashboardManagerImpl.getInstance(project).customizationsDto
  }

  override suspend fun updateConfigurationFolderName(serviceIds: List<RunDashboardServiceId>, newGroupName: String?, projectId: ProjectId) {
    val project = projectId.findProjectOrNull() ?: return
    val dashboardManagerImpl = RunDashboardManagerImpl.getInstance(project)
    val backendServices = serviceIds.mapNotNull { dashboardManagerImpl.findServiceById(it) }

    val runManager = RunManagerImpl.getInstanceImpl(project)
    runManager.fireBeginUpdate()
    try {
      backendServices.forEach { node -> node.getConfigurationSettings().setFolderName(newGroupName) }
    }
    finally {
      runManager.fireEndUpdate()
    }
  }

  override suspend fun getLuxedContentEvents(projectId: ProjectId): Flow<RunDashboardLuxedContentEvent> {
    val project = projectId.findProjectOrNull() ?: return emptyFlow()
    return BackendLuxedRunDashboardContentManager.getInstance(project).getLuxedContents()
  }

  override suspend fun startLuxingContentForService(projectId: ProjectId, id: RunDashboardServiceId): ComponentDirectTransferId? {
    val project = projectId.findProjectOrNull() ?: return null
    return withContext(Dispatchers.EDT) {
      BackendLuxedRunDashboardContentManager.getInstance(project).startLuxing(id)
    }
  }

  override suspend fun pauseLuxingContentForService(projectId: ProjectId, id: RunDashboardServiceId) {
    val project = projectId.findProjectOrNull() ?: return
    BackendLuxedRunDashboardContentManager.getInstance(project).pauseLuxing(id)
  }
}