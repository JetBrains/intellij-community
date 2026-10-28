// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.rpc

import com.intellij.platform.project.ProjectId
import com.intellij.platform.rpc.Id
import com.intellij.platform.rpc.RemoteApiProviderService
import com.intellij.platform.rpc.UID
import fleet.rpc.RemoteApi
import fleet.rpc.Rpc
import fleet.rpc.core.SendChannelSerializer
import fleet.rpc.remoteApiDescriptor
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Rpc
interface RunSessionsApi : RemoteApi<Unit> {

  suspend fun events(projectId: ProjectId): Flow<RunSessionEvent>

  suspend fun getSession(projectId: ProjectId, runTabId: RunSessionId): RunSession?

  companion object {
    @JvmStatic
    suspend fun getInstance(): RunSessionsApi {
      return RemoteApiProviderService.resolve(remoteApiDescriptor<RunSessionsApi>())
    }
  }
}

@ApiStatus.Internal
@Serializable
sealed interface RunSessionEvent {

  @Serializable
  class SessionStarted(val runTabId: RunSessionId, val runSession: RunSession) : RunSessionEvent

}

@ApiStatus.Internal
@Serializable
data class RunSessionId(override val uid: UID) : Id

@ApiStatus.Internal
@Serializable
data class RunSession(
  val executorEnvironment: ExecutionEnvironmentProxyDto,
  val processHandler: ProcessHandlerDto?,
  @Serializable(with = SendChannelSerializer::class) val tabClosedCallback: SendChannel<Unit>,
)

