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
  suspend fun updateConfigurationFolderName(serviceIds: List<RunDashboardServiceId>, newGroupName: String?, projectId: ProjectId)
  suspend fun getLuxedContentEvents(projectId: ProjectId): Flow<RunDashboardLuxedContentEvent>
  suspend fun startLuxingContentForService(projectId: ProjectId, id: RunDashboardServiceId): ComponentDirectTransferId?
  suspend fun pauseLuxingContentForService(projectId: ProjectId, id: RunDashboardServiceId)
}