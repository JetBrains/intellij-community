// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.impl.base

import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import kotlin.coroutines.cancellation.CancellationException


@ApiStatus.Internal
class ProcessFunctions(
  val waitForExit: suspend () -> Unit,
  private val killProcess: suspend () -> Unit,
) {

  private suspend fun killAndJoinInternal(
    warn: (String) -> Unit,
    processNameForDebug: String,
  ) {
    warn("Sending kill to $processNameForDebug")
    killProcess()
    warn("Kill sent to $processNameForDebug, waiting")
    waitForExit()
    warn("Process $processNameForDebug died")
  }

  suspend fun killAndJoin(
    warn: (String) -> Unit,
    processNameForDebug: String,
    killExecutionScope: CoroutineScope? = null,
  ) {
    if (killExecutionScope == null) {
      withContext(NonCancellable) {
        killAndJoinInternal(warn, processNameForDebug)
      }
    }
    else {
      val killJob = killExecutionScope.launch(CoroutineName("kill+join $processNameForDebug") + Dispatchers.IO) {
        killAndJoinInternal(warn, processNameForDebug)
      }
      withContext(NonCancellable) {
        killJob.join()
      }
    }
  }
}

/**
 * This is an implementation detail to be reused by other parts of a system.
 * Do not call it directly. Use [com.intellij.platform.eel.EelExecApi.ExecuteProcessOptions.scope]
 */
@ApiStatus.Internal
fun CoroutineScope.bindProcessToScopeImpl(
  warn: (String) -> Unit,
  processNameForDebug: String,
  killExecutionScope: CoroutineScope? = null,
  processFunctions: ProcessFunctions,
) {
  val context = CoroutineName("Waiting for process $processNameForDebug") + Dispatchers.IO

  suspend fun killAndJoin() {
    processFunctions.killAndJoin(warn, processNameForDebug, killExecutionScope)
  }

  if (!isActive) {
    warn("Scope $this is dead, killing process $processNameForDebug")
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
