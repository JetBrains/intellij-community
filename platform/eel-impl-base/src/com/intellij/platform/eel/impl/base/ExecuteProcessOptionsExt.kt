// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.impl.base

import com.intellij.platform.eel.EelExecApi
import com.intellij.platform.eel.EelExecWindowsApi
import com.intellij.platform.eel.EelProcess
import com.intellij.platform.util.annotations.VisibleToClasses
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
@VisibleToClasses(
  "com.intellij.platform.eel.impl.local.EelLocalExecPosixApi",
  "com.intellij.platform.eel.impl.local.EelLocalExecWindowsApi",
  "com.intellij.platform.ijent.impl.base.GrpcIjentExecApiDelegate",
)
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
  get() {
    // Note: args aren't escaped, hence this command line can't be used. It is here for debug purposes only.
    return when (this) {
      is EelExecWindowsApi.ExecuteProcessWindowsOptions if this.commandLine.isNotEmpty() -> this.lpCommandLine
      else -> (listOf(exe) + args).joinToString(" ")
    }
  }

/**
 * `true` when the options ask for what only a direct `CreateProcessW` call gives: a raw
 * [EelExecWindowsApi.ExecuteProcessWindowsOptions.commandLine], [EelExecWindowsApi.ExecuteProcessWindowsOptions.startSuspended] or
 * [EelExecWindowsApi.ExecuteProcessWindowsOptions.newConsole]. `ProcessBuilder` and the IJent protocol offer none of the three.
 *
 * Options without them run the same way as [EelExecApi.ExecuteProcessOptions], with the argument list.
 */
@get:ApiStatus.Internal
val EelExecWindowsApi.ExecuteProcessWindowsOptions.needsCreateProcessW: Boolean
  get() = commandLine.isNotEmpty() || startSuspended || newConsole

/**
 * The `lpCommandLine` that `CreateProcessW` gets for these options. See [lpCommandLine] for the rule.
 *
 * The value of [EelExecWindowsApi.ExecuteProcessWindowsOptions.commandLine] reaches the child without a change. An empty one gives
 * the child the quoted `exe` alone.
 */
@get:ApiStatus.Internal
val EelExecWindowsApi.ExecuteProcessWindowsOptions.lpCommandLine: String
  get() = lpCommandLine(exe, commandLine)

/**
 * The `lpCommandLine` that `CreateProcessW` gets for [exe] and [arguments].
 *
 * Windows hands the child one string, and the child reads its own `argv[0]` from the first token of that string. So the first
 * token must be [exe]. This function puts [exe] in double quotes, then it adds [arguments] after a space.
 *
 * A Windows path holds no double quote, so nothing inside the quotes needs an escape. An [exe] that already carries the quotes
 * keeps them. Empty [arguments] give the quoted [exe] alone.
 */
@ApiStatus.Internal
fun lpCommandLine(exe: String, arguments: String): String {
  val trimmedExe = exe.trim()
  val quotedExe =
    if (trimmedExe.length >= 2 && trimmedExe.startsWith('"') && trimmedExe.endsWith('"')) trimmedExe
    else "\"$trimmedExe\""
  return if (arguments.isEmpty()) quotedExe else "$quotedExe $arguments"
}

private val logger = Logger.getLogger("com.intellij.platform.eel.impl.ExecuteProcessOptionsExt")
