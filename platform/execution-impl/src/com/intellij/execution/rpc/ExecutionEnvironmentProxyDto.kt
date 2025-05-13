// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.rpc

import com.intellij.execution.runners.BackendExecutionEnvironmentProxy
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.ExecutionUtil
import com.intellij.ide.ui.icons.IconId
import com.intellij.ide.ui.icons.rpcId
import com.intellij.openapi.util.NlsSafe
import fleet.rpc.core.RpcFlow
import fleet.rpc.core.SendChannelSerializer
import fleet.rpc.core.toRpc
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.jetbrains.annotations.ApiStatus

@Serializable
data class ExecutionEnvironmentProxyDto(
  val runProfileName: @NlsSafe String,
  val icon: IconId,
  val rerunIcon: IconId,
  val isStartingInitial: Boolean,
  val isStarting: RpcFlow<Boolean>,
  @Serializable(with = SendChannelSerializer::class) val restartRequest: SendChannel<Unit>,
  // TODO: this is only for backward compatibility for Monolith. Should be migrated to Proxies
  @Transient val executionEnvironment: ExecutionEnvironment? = null,
)

@ApiStatus.Internal
fun ExecutionEnvironment.toDto(cs: CoroutineScope): ExecutionEnvironmentProxyDto {
  val environment = this
  val proxy = BackendExecutionEnvironmentProxy(environment)
  val restartRequestChannel = Channel<Unit>(capacity = 1)
  cs.launch {
    for (request in restartRequestChannel) {
      ExecutionUtil.restart(environment)
    }
  }
  return ExecutionEnvironmentProxyDto(
    proxy.getRunProfileName(), proxy.getIcon().rpcId(), proxy.getRerunIcon().rpcId(),
    proxy.isStarting(), proxy.isStartingFlow().toRpc(cs.coroutineContext),
    restartRequestChannel,
    this
  )
}