// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.execution.dashboard.splitApi

import com.intellij.execution.RunContentDescriptorIdImpl
import com.intellij.execution.dashboard.RunDashboardServiceId
import com.intellij.platform.project.ProjectId
import com.intellij.platform.rpc.RemoteApiProviderService
import fleet.rpc.RemoteApi
import fleet.rpc.Rpc
import fleet.rpc.remoteApiDescriptor
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Rpc
interface RunDashboardManagerRpc : RemoteApi<Unit> {
  companion object {
    @JvmStatic
    suspend fun getInstance(): RunDashboardManagerRpc {
      return RemoteApiProviderService.resolve(remoteApiDescriptor<RunDashboardManagerRpc>())
    }
  }

  suspend fun reorderConfigurations(projectId: ProjectId, targetId: RunDashboardServiceId, dropId: RunDashboardServiceId, position: String)
  suspend fun tryNavigate(projectId: ProjectId, serviceId: RunDashboardServiceId, requestFocus: Boolean)
  suspend fun removeService(projectId: ProjectId, serviceId: RunDashboardServiceId)
  suspend fun removeFolderGroup(projectId: ProjectId, folderGroupName: String)
  suspend fun rerunConfiguration(projectId: ProjectId, serviceId: RunDashboardServiceId)
  suspend fun addServiceToFolder(projectId: ProjectId, dropId: RunDashboardServiceId, targetFolderName: String)
  suspend fun tryNavigateLink(projectId: ProjectId, link: String, serviceId: RunDashboardServiceId)
  suspend fun attachRunContentDescriptorId(projectId: ProjectId,
                                           oldDescriptorId: RunContentDescriptorIdImpl?,
                                           newDescriptorId: RunContentDescriptorIdImpl)
  suspend fun detachRunContentDescriptorId(projectId: ProjectId, descriptorId: RunContentDescriptorIdImpl)
  suspend fun setConfigurationTypes(projectId: ProjectId, configurationTypes: Set<String>)
}