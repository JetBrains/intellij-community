// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.rpc

import com.intellij.execution.runners.BackendExecutionEnvironmentProxy
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.ExecutionEnvironmentProxy
import com.intellij.ide.ui.icons.IconId
import com.intellij.ide.ui.icons.rpcId
import com.intellij.openapi.util.NlsSafe
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

@Serializable
data class ExecutionEnvironmentProxyDto(
  val runProfileName: @NlsSafe String,
  val icon: IconId,
  val rerunIcon: IconId,
  val isStarting: Boolean, // TODO: should be reactive
)

@ApiStatus.Internal
fun ExecutionEnvironmentProxy.toDto(): ExecutionEnvironmentProxyDto {
  return ExecutionEnvironmentProxyDto(getRunProfileName(), getIcon().rpcId(), getRerunIcon().rpcId(), isStarting())
}

@ApiStatus.Internal
fun ExecutionEnvironment.toDto(): ExecutionEnvironmentProxyDto {
  return BackendExecutionEnvironmentProxy(this).toDto()
}