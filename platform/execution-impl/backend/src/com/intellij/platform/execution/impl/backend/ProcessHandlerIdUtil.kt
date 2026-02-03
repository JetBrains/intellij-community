// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.execution.impl.backend

import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.rpc.ProcessHandlerId
import com.intellij.platform.kernel.ids.BackendValueIdType
import com.intellij.platform.kernel.ids.findValueById
import com.intellij.platform.kernel.ids.storeValueGlobally
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
fun ProcessHandlerId.findValue(): ProcessHandler? {
  return findValueById(this, type = ProcessHandlerValueIdType)
}

@ApiStatus.Internal
fun ProcessHandler.storeGlobally(coroutineScope: CoroutineScope): ProcessHandlerId {
  return storeValueGlobally(coroutineScope, this, type = ProcessHandlerValueIdType)
}


@ApiStatus.Internal
private object ProcessHandlerValueIdType : BackendValueIdType<ProcessHandlerId, ProcessHandler>(::ProcessHandlerId)