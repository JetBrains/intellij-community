// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.impl

import com.intellij.execution.process.ProcessHandler
import com.intellij.openapi.extensions.ExtensionPointName
import kotlinx.coroutines.Deferred
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface ProcessHandlerPidProvider {

  companion object {
    val EP_NAME: ExtensionPointName<ProcessHandlerPidProvider> =
      ExtensionPointName.create("com.intellij.execution.processHandlerPidProvider")
  }

  /**
   * @return `null` if [processHandler] is not supported by this implementation of [ProcessHandlerPidProvider]
   */
  fun getPid(processHandler: ProcessHandler): Deferred<Long?>?
}