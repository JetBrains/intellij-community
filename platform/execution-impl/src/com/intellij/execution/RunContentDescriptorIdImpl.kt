// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution

import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.platform.kernel.ids.BackendValueIdType
import com.intellij.platform.kernel.ids.findValueById
import com.intellij.platform.rpc.UID
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Serializable
class RunContentDescriptorIdImpl(override val uid: UID) : RunContentDescriptorId

@ApiStatus.Internal
fun RunContentDescriptorId.findContentValue(): RunContentDescriptor? {
  return findValueById(this, type = RunContentDescriptorIdType)
}

@ApiStatus.Internal
object RunContentDescriptorIdType : BackendValueIdType<RunContentDescriptorId, RunContentDescriptor>(::RunContentDescriptorIdImpl)