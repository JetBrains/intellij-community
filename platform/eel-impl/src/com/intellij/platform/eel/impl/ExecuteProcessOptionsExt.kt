// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.impl

import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.platform.eel.EelExecApi
import com.intellij.platform.eel.EelProcess
import com.intellij.platform.eel.provider.utils.ProcessFunctions
import com.intellij.platform.eel.provider.utils.bindProcessToScopeImpl
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.jetbrains.annotations.ApiStatus
import kotlin.coroutines.cancellation.CancellationException

/**
 * If [scope] is set, bind [process] to it, so it gets killed as soon as the scope finishes.
 * Used by implementors to support [EelExecApi.ExecuteProcessOptions.scope]
 */
@ApiStatus.Internal
fun EelExecApi.ExecuteProcessOptions.bindProcessToScopeIfSet(process: EelProcess) {
  scope?.bindProcessToScopeImpl(
    logger = logger,
    processNameForDebug = commandLineForDebug,
    ProcessFunctions(
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
internal val EelExecApi.ExecuteProcessOptions.commandLineForDebug: String
  get() = // Note: args aren't escaped, hence this command line can't be used. It is here for debug purposes only.
    (listOf(exe) + args).joinToString(" ")
private val logger = fileLogger()
