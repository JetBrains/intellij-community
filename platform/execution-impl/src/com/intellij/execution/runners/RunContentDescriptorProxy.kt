// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.runners

import com.intellij.execution.impl.ExecutionManagerImpl
import com.intellij.execution.ui.RunContentDescriptor
import org.jetbrains.annotations.ApiStatus

/**
 * This interface abstracts the functionality of [com.intellij.execution.ui.RunContentDescriptor],
 * so it can be implemented differently on frontend and backend sides.
 */
@ApiStatus.Internal
interface RunContentDescriptorProxy {
  fun isProcessRunning(): Boolean

  fun getProcessHandlerProxy(): ProcessHandlerProxy?
}

@ApiStatus.Internal
class BackendRunContentDescriptorProxy(private val runContentDescriptor: RunContentDescriptor) : RunContentDescriptorProxy {
  override fun isProcessRunning(): Boolean {
    return ExecutionManagerImpl.isProcessRunning(runContentDescriptor)
  }

  override fun getProcessHandlerProxy(): ProcessHandlerProxy? {
    val processHandler = runContentDescriptor.processHandler ?: return null
    return BackendProcessHandlerProxy(processHandler)
  }
}