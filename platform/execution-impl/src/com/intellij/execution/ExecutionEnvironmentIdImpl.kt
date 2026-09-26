// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution

import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.ide.CustomDataContextSerializer
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.DataSnapshot
import com.intellij.openapi.actionSystem.ExecutionDataKeys
import com.intellij.openapi.actionSystem.UiDataRule
import com.intellij.platform.kernel.ids.BackendValueIdType
import com.intellij.platform.kernel.ids.findValueById
import com.intellij.platform.kernel.ids.storeValueGlobally
import com.intellij.platform.rpc.Id
import com.intellij.platform.rpc.UID
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Serializable
data class ExecutionEnvironmentIdImpl(override val uid: UID) : Id

@ApiStatus.Internal
fun ExecutionEnvironmentIdImpl.findExecutionEnvironment(): ExecutionEnvironment? {
  return findValueById(this, type = ExecutionEnvironmentIdType)
}

@ApiStatus.Internal
fun ExecutionEnvironment.storeGlobally(cs: CoroutineScope): ExecutionEnvironmentIdImpl {
  return storeValueGlobally(cs, this, ExecutionEnvironmentIdType)
}

private object ExecutionEnvironmentIdType :
  BackendValueIdType<ExecutionEnvironmentIdImpl, ExecutionEnvironment>(::ExecutionEnvironmentIdImpl)

@ApiStatus.Internal
@JvmField
val EXECUTION_ENVIRONMENT_ID: DataKey<ExecutionEnvironmentIdImpl> = DataKey.create("EXECUTION_ENVIRONMENT_ID")

internal class ExecutionEnvironmentIdSerializer : CustomDataContextSerializer<ExecutionEnvironmentIdImpl> {
  override val key: DataKey<ExecutionEnvironmentIdImpl> = EXECUTION_ENVIRONMENT_ID
  override val serializer: KSerializer<ExecutionEnvironmentIdImpl> = ExecutionEnvironmentIdImpl.serializer()
}

internal class ExecutionEnvironmentIdDataRule : UiDataRule {
  override fun uiDataSnapshot(sink: DataSink, snapshot: DataSnapshot) {
    if (snapshot[ExecutionDataKeys.EXECUTION_ENVIRONMENT] != null) return
    val environment = snapshot[EXECUTION_ENVIRONMENT_ID]?.findExecutionEnvironment() ?: return
    sink[ExecutionDataKeys.EXECUTION_ENVIRONMENT] = environment
  }
}
