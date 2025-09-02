// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.execution.impl.shared

import com.intellij.platform.project.ProjectId
import com.intellij.platform.rpc.RemoteApiProviderService
import fleet.rpc.RemoteApi
import fleet.rpc.Rpc
import fleet.rpc.remoteApiDescriptor
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Rpc
interface JavaAutoRunFloatingToolbarApi : RemoteApi<Unit> {
  suspend fun setToolbarEnabled(projectId: ProjectId, enabled: Boolean)
  suspend fun disableAllAutoTests(projectId: ProjectId)

  suspend fun toolbarStatus(projectId: ProjectId): Flow<JavaAutoRunFloatingToolbarStatus>?

  companion object {
    suspend fun getInstance(): JavaAutoRunFloatingToolbarApi {
      return RemoteApiProviderService.resolve(remoteApiDescriptor<JavaAutoRunFloatingToolbarApi>())
    }
  }
}

@ApiStatus.Internal
@Serializable
data class JavaAutoRunFloatingToolbarStatus(val autoTestEnabled: Boolean, val toolbarEnabled: Boolean)
