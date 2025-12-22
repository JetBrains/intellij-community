// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution

import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.ide.CustomDataContextSerializer
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.DataSnapshot
import com.intellij.openapi.actionSystem.LangDataKeys.RUN_CONTENT_DESCRIPTOR
import com.intellij.openapi.actionSystem.UiDataRule
import com.intellij.platform.kernel.ids.BackendValueIdType
import com.intellij.platform.kernel.ids.findValueById
import com.intellij.platform.kernel.ids.storeValueGlobally
import com.intellij.platform.rpc.UID
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Serializable
data class RunContentDescriptorIdImpl(override val uid: UID) : RunContentDescriptorId

@ApiStatus.Internal
fun RunContentDescriptorIdImpl.findContentValue(): RunContentDescriptor? {
  return findValueById(this, type = RunContentDescriptorIdType)
}

@ApiStatus.Internal
fun RunContentDescriptor.storeGlobally(cs: CoroutineScope): RunContentDescriptorIdImpl {
  return storeValueGlobally(cs, this, RunContentDescriptorIdType)
}

private object RunContentDescriptorIdType : BackendValueIdType<RunContentDescriptorIdImpl, RunContentDescriptor>(::RunContentDescriptorIdImpl)

@ApiStatus.Internal
@JvmField
val RUN_CONTENT_DESCRIPTOR_ID: DataKey<RunContentDescriptorIdImpl> = DataKey.create("RUN_CONTENT_DESCRIPTOR_ID")

internal class RunContentDescriptorIdSerializer() : CustomDataContextSerializer<RunContentDescriptorIdImpl> {
  override val key: DataKey<RunContentDescriptorIdImpl> = RUN_CONTENT_DESCRIPTOR_ID
  override val serializer: KSerializer<RunContentDescriptorIdImpl> = RunContentDescriptorIdImpl.serializer()
}

internal class RunContentDescriptorIdDataRule : UiDataRule {
  override fun uiDataSnapshot(sink: DataSink, snapshot: DataSnapshot) {
    if (snapshot[RUN_CONTENT_DESCRIPTOR] != null) return
    val descriptor = snapshot[RUN_CONTENT_DESCRIPTOR_ID]?.findContentValue() ?: return
    sink[RUN_CONTENT_DESCRIPTOR] = descriptor
  }
}