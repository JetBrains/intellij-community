// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.provider.utils

import com.intellij.openapi.diagnostic.Logger
import kotlinx.coroutines.*
import org.jetbrains.annotations.ApiStatus
import kotlin.coroutines.cancellation.CancellationException


@ApiStatus.Internal
data class ProcessFunctions(
  val waitForExit: suspend () -> Unit,
  val killProcess: suspend () -> Unit,
)

/**
 * This is an implementation detail to be reused by other parts of a system.
 * Do not call it directly, use [bindToScope]
 */
@ApiStatus.Internal
fun CoroutineScope.bindProcessToScopeImpl(
  logger: Logger,
  processNameForDebug: String,
  processFunctions: ProcessFunctions,
) {
  val context = CoroutineName("Waiting for process $processNameForDebug") + Dispatchers.IO

  suspend fun killAndJoin() {
    withContext(NonCancellable) {
      logger.warn("Sending kill to $processNameForDebug")
      processFunctions.killProcess()
      logger.warn("Kill send to $processNameForDebug, waiting")
      processFunctions.waitForExit()
      logger.warn("Process $processNameForDebug died")
    }
  }

  if (!isActive) {
    logger.warn("Scope $this is dead, killing process $processNameForDebug")
    launch(context, start = CoroutineStart.UNDISPATCHED) {
      killAndJoin()
    }
  }

  launch(context, start = CoroutineStart.UNDISPATCHED) {
    try {
      processFunctions.waitForExit()
    }
    catch (e: CancellationException) {
      killAndJoin()
      throw e
    }
  }
}

