// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.rpc

import com.intellij.execution.runners.BackendExecutionEnvironmentProxy
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.ExecutionEnvironmentProxy
import com.intellij.ide.ui.icons.IconId
import com.intellij.ide.ui.icons.rpcId
import com.intellij.openapi.util.NlsSafe
import fleet.rpc.core.RpcFlow
import fleet.rpc.core.toRpc
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

@Serializable
data class ExecutionEnvironmentProxyDto(
  val runProfileName: @NlsSafe String,
  val icon: IconId,
  val rerunIcon: IconId,
  val isStartingInitial: Boolean,
  val isStarting: RpcFlow<Boolean>,
)

@ApiStatus.Internal
fun ExecutionEnvironmentProxy.toDto(): ExecutionEnvironmentProxyDto {
  // TODO: think about the coroutineContext which is passed to `toRpc`
  //   since ExecutionEnvironment.toDto function should be non suspend,
  //   we cannot use suspend alternative of `toRpc` which takes current coroutine context
  return ExecutionEnvironmentProxyDto(getRunProfileName(), getIcon().rpcId(), getRerunIcon().rpcId(), isStarting(), isStartingFlow().toRpc(Dispatchers.IO))
}

@ApiStatus.Internal
fun ExecutionEnvironment.toDto(): ExecutionEnvironmentProxyDto {
  return BackendExecutionEnvironmentProxy(this).toDto()
}