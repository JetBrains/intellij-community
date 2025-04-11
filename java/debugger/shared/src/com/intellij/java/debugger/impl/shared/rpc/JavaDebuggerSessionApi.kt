// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.debugger.impl.shared.rpc

import com.intellij.execution.filters.Filter
import com.intellij.ide.ui.icons.IconId
import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.rpc.RemoteApiProviderService
import com.intellij.xdebugger.impl.rpc.SerializableSimpleTextAttributes
import com.intellij.xdebugger.impl.rpc.XDebugSessionId
import fleet.rpc.RemoteApi
import fleet.rpc.Rpc
import fleet.rpc.core.ReceiveChannelSerializer
import fleet.rpc.remoteApiDescriptor
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Rpc
interface JavaDebuggerSessionApi : RemoteApi<Unit> {

  suspend fun dumpThreads(sessionId: XDebugSessionId): JavaThreadDumpResponseDto?

  companion object {
    @JvmStatic
    suspend fun getInstance(): JavaDebuggerSessionApi {
      return RemoteApiProviderService.resolve(remoteApiDescriptor<JavaDebuggerSessionApi>())
    }
  }
}

@ApiStatus.Internal
@Serializable
data class JavaThreadDumpResponseDto(
  @Serializable(with = ReceiveChannelSerializer::class) val dumps: ReceiveChannel<JavaThreadDumpDto>,
  @Transient val filters: List<Filter> = emptyList(), // TODO pass to FE
)

@ApiStatus.Internal
@Serializable
data class JavaThreadDumpDto(
  val threadDump: List<JavaThreadDumpItemDto>,
  val mergedThreadDump: List<JavaThreadDumpItemDto>,
)

@ApiStatus.Internal
@Serializable
data class JavaThreadDumpItemDto(
  val name: @NlsSafe String,
  val stateDesc: @NlsSafe String,
  val stackTrace: @NlsSafe String,
  val interestLevel: Int,
  val iconId: IconId,
  val attributes: SerializableSimpleTextAttributes,
)
