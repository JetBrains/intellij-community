// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.rpc

import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.project.ProjectId
import com.intellij.platform.rpc.Id
import com.intellij.platform.rpc.RemoteApiProviderService
import com.intellij.platform.rpc.UID
import fleet.rpc.RemoteApi
import fleet.rpc.Rpc
import fleet.rpc.core.RpcFlow
import fleet.rpc.remoteApiDescriptor
import kotlinx.coroutines.Deferred
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

@Rpc
interface ProcessHandlerApi : RemoteApi<Unit> {
  suspend fun startNotify(handlerId: ProcessHandlerId)

  suspend fun waitFor(project: ProjectId, handlerId: ProcessHandlerId, timeoutInMilliseconds: Long?): Deferred<Boolean>

  suspend fun destroyProcess(handlerId: ProcessHandlerId): Deferred<Int?>

  suspend fun detachProcess(handlerId: ProcessHandlerId): Deferred<Int?>

  suspend fun killProcess(handlerId: ProcessHandlerId)

  companion object {
    @JvmStatic
    suspend fun getInstance(): ProcessHandlerApi {
      return RemoteApiProviderService.resolve(remoteApiDescriptor<ProcessHandlerApi>())
    }
  }
}

@Serializable
data class ProcessHandlerId(override val uid: UID) : Id

@Serializable
data class KillableProcessInfo(
  val canKillProcess: Boolean = true,
)

@Serializable
data class ProcessHandlerDto(
  val processHandlerId: ProcessHandlerId,
  val detachIsDefault: Boolean,
  val processHandlerEvents: RpcFlow<ProcessHandlerEvent>,
  val killableProcessInfo: KillableProcessInfo? = null,
)

@Serializable
sealed interface ProcessHandlerEvent {
  @Serializable
  data class StartNotified(val eventData: ProcessHandlerEventData) : ProcessHandlerEvent

  @Serializable
  data class ProcessTerminated(val eventData: ProcessHandlerEventData) : ProcessHandlerEvent

  @Serializable
  data class ProcessWillTerminate(val eventData: ProcessHandlerEventData, val willBeDestroyed: Boolean) : ProcessHandlerEvent

  @Serializable
  data class OnTextAvailable(val eventData: ProcessHandlerEventData, val key: String) : ProcessHandlerEvent

  @Serializable
  data object ProcessNotStarted : ProcessHandlerEvent
}

@ApiStatus.Internal
@Serializable
data class ProcessHandlerEventData(
  val text: @NlsSafe String?,
  val exitCode: Int,
)