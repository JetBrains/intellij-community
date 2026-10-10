// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.execution.serviceView.splitApi

import com.intellij.ide.rpc.awaitWithLocalFallback
import com.intellij.ide.ui.icons.IconId
import com.intellij.ide.vfs.VirtualFileId
import com.intellij.platform.project.ProjectId
import com.intellij.platform.rpc.lite.LiteRemoteApiProviderService
import fleet.rpc.RemoteApi
import fleet.rpc.Rpc
import fleet.rpc.remoteApiDescriptor
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Rpc
interface ServiceViewRpc : RemoteApi<Unit> {
  companion object {
    /**
     * Resolves the API of this session. A light process has no backend to await, so it gets the in-process [ServiceViewRpcImpl];
     * every other mode awaits the backend connection, as before.
     */
    @JvmStatic
    suspend fun getInstance(): ServiceViewRpc {
      return LiteRemoteApiProviderService.awaitWithLocalFallback(remoteApiDescriptor<ServiceViewRpc>()) { ServiceViewRpcImpl() }
    }
  }

  suspend fun findServices(fileId: VirtualFileId, projectId: ProjectId): List<String>
  suspend fun loadConfigurationTypes(projectId: ProjectId): ServiceViewConfigurationTypeSettings?
  suspend fun saveConfigurationTypes(projectId: ProjectId, includedTypes: Set<String>)

  suspend fun changeServiceViewImplementationForNextIdeRunAndRestart(shouldEnableSplitImplementation: Boolean)
}

@ApiStatus.Internal
@Serializable
data class ServiceViewConfigurationTypeSettings(
  val included: List<ServiceViewConfigurationType>,
  val excluded: List<ServiceViewConfigurationType>,
)

@ApiStatus.Internal
@Serializable
data class ServiceViewConfigurationType(
  val typeId: String,
  val displayName: String,
  val iconId: IconId,
)