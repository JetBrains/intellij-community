// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.impl.base

import com.intellij.platform.eel.EelExecApi
import com.intellij.platform.eel.EelProcess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.jetbrains.annotations.ApiStatus
import java.util.logging.Logger
import kotlin.coroutines.cancellation.CancellationException

/**
 * If [scope] is set, bind [process] to it, so it gets killed as soon as the scope finishes.
 * Used by implementors to support [scope]
 */
@ApiStatus.Internal
fun EelExecApi.ExecuteProcessOptions.bindProcessToScopeIfSet(
  process: EelProcess,
  killExecutionScope: CoroutineScope? = null,
) {
  scope?.bindProcessToScopeImpl(
    warn = logger::warning,
    processNameForDebug = commandLineForDebug,
    killExecutionScope = killExecutionScope,
    processFunctions = ProcessFunctions(
      waitForExit = {
        try {
          process.exitCode.await()
        }
        catch (@Suppress("IncorrectCancellationExceptionHandling") _: CancellationException) {
          currentCoroutineContext().ensureActive()
          // Ignore if something destroyed the scope of the process.
        }
      },
      killProcess = { process.kill() }
    )
  )
}

@get:ApiStatus.Internal
val EelExecApi.ExecuteProcessOptions.commandLineForDebug: String
  get() = // Note: args aren't escaped, hence this command line can't be used. It is here for debug purposes only.
    (listOf(exe) + args).joinToString(" ")
private val logger = Logger.getLogger("com.intellij.platform.eel.impl.ExecuteProcessOptionsExt")
