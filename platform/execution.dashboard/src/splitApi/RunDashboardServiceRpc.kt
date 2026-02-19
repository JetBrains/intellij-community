// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.execution.dashboard.splitApi

import com.intellij.execution.dashboard.RunDashboardServiceId
import com.intellij.ide.rpc.ComponentDirectTransferId
import com.intellij.platform.project.ProjectId
import com.intellij.platform.rpc.RemoteApiProviderService
import fleet.rpc.RemoteApi
import fleet.rpc.Rpc
import fleet.rpc.remoteApiDescriptor
import kotlinx.coroutines.flow.Flow
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Rpc
interface RunDashboardServiceRpc : RemoteApi<Unit> {
  companion object {
    @JvmStatic
    suspend fun getInstance(): RunDashboardServiceRpc {
      return RemoteApiProviderService.resolve(remoteApiDescriptor<RunDashboardServiceRpc>())
    }
  }

  suspend fun getSettings(projectId: ProjectId): Flow<RunDashboardSettingsDto>
  suspend fun getServices(projectId: ProjectId): Flow<List<RunDashboardServiceDto>>
  suspend fun getStatuses(projectId: ProjectId): Flow<ServiceStatusDto>
  suspend fun getCustomizations(projectId: ProjectId): Flow<ServiceCustomizationDto>
  suspend fun getConfigurationTypes(projectId: ProjectId): Flow<Set<String>>
  suspend fun getNavigateToServiceEvents(projectId: ProjectId) : Flow<NavigateToServiceEvent>
  suspend fun updateConfigurationFolderName(projectId: ProjectId, serviceIds: List<RunDashboardServiceId>, newGroupName: String?)
  suspend fun getLuxedContentEvents(projectId: ProjectId): Flow<RunDashboardLuxedContentEvent>
  suspend fun startLuxingContentForService(projectId: ProjectId, id: RunDashboardServiceId): ComponentDirectTransferId?
  suspend fun pauseLuxingContentForService(projectId: ProjectId, id: RunDashboardServiceId)
  suspend fun getAvailableConfigurations(projectId: ProjectId): Flow<Set<RunDashboardConfigurationDto>>
  suspend fun getExcludedConfigurations(projectId: ProjectId): Flow<Set<String>>
  suspend fun setNewExcluded(projectId: ProjectId, configurationTypeId: String, newExcluded: Boolean)
  suspend fun restoreConfigurations(projectId: ProjectId, configurations: List<RunDashboardConfigurationId>)
  suspend fun hideConfigurations(projectId: ProjectId, configurations: List<RunDashboardConfigurationId>)
  suspend fun getRunManagerUpdates(projectId: ProjectId): Flow<Unit>
  suspend fun editConfiguration(projectId: ProjectId, serviceId: RunDashboardServiceId)
  suspend fun copyConfiguration(projectId: ProjectId, serviceId: RunDashboardServiceId)
  suspend fun hideConfiguration(projectId: ProjectId, serviceIds: List<RunDashboardServiceId>)
}