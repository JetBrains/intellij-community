// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.debugger.impl.shared.rpc

import com.intellij.platform.rpc.RemoteApiProviderService
import com.intellij.xdebugger.impl.rpc.XDebugSessionId
import fleet.rpc.RemoteApi
import fleet.rpc.Rpc
import fleet.rpc.core.RpcFlow
import fleet.rpc.remoteApiDescriptor
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Rpc
interface JavaDebuggerManagerApi : RemoteApi<Unit> {
  suspend fun getJavaSession(sessionId: XDebugSessionId): JavaDebuggerSessionDto?

  companion object {
    @JvmStatic
    suspend fun getInstance(): JavaDebuggerManagerApi {
      return RemoteApiProviderService.resolve(remoteApiDescriptor<JavaDebuggerManagerApi>())
    }
  }
}

@ApiStatus.Internal
@Serializable
data class JavaDebuggerSessionDto(
  val initialState: JavaSessionState,
  val stateFlow: RpcFlow<JavaSessionState>,
)

@ApiStatus.Internal
@Serializable
data class JavaSessionState(
  val isAttached: Boolean,
  val isEvaluationPossible: Boolean,
)

