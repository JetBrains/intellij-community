// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
/**
 * Later, this file will be moved close to `WSLDistribution`.
 */
@file:JvmName("IjentWslLauncher")

package com.intellij.execution.wsl.ijent

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.wsl.WSLCommandLineOptions
import com.intellij.execution.wsl.WSLDistribution
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.project.Project
import com.intellij.platform.ijent.IjentApi
import com.intellij.platform.ijent.IjentExecFileProvider
import com.intellij.platform.ijent.IjentSessionProvider
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.VisibleForTesting
import kotlin.io.path.absolutePathString

suspend fun deployAndLaunchIjent(
  ijentCoroutineScope: CoroutineScope,
  project: Project?,
  wslDistribution: WSLDistribution,
  wslCommandLineOptions: WSLCommandLineOptions = WSLCommandLineOptions(),
): IjentApi =
  deployAndLaunchIjentGettingPath(ijentCoroutineScope, project, wslDistribution, wslCommandLineOptions).second

@VisibleForTesting
suspend fun deployAndLaunchIjentGettingPath(
  ijentCoroutineScope: CoroutineScope,
  project: Project?,
  wslDistribution: WSLDistribution,
  wslCommandLineOptions: WSLCommandLineOptions = WSLCommandLineOptions(),
): Pair<String, IjentApi> {
  val ijentBinary = IjentExecFileProvider.getIjentBinary(IjentExecFileProvider.SupportedPlatform.X86_64__LINUX)

  val wslIjentBinary = wslDistribution.getWslPath(ijentBinary.absolutePathString())!!

  val (debuggingLogLevel, backtrace) = when {
    LOG.isTraceEnabled -> "trace" to true
    LOG.isDebugEnabled -> "debug" to true
    else -> "info" to false
  }

  val commandLine = GeneralCommandLine(listOfNotNull(
    // It's supposed that WslDistribution always converts commands into SHELL.
    // There's no strict reason to call 'exec', just a tiny optimization.
    "exec",

    "/usr/bin/env",
    "RUST_LOG=ijent=$debuggingLogLevel",
    if (backtrace) "RUST_BACKTRACE=1" else null,
    // "gdbserver", "0.0.0.0:12345",  // https://sourceware.org/gdb/onlinedocs/gdb/Connecting.html
    wslIjentBinary,
    "grpc-stdio-server",
  ))
  wslDistribution.patchCommandLine(commandLine, project, wslCommandLineOptions)

  LOG.debug {
    "Going to launch IJent: ${commandLine.commandLineString}"
  }

  val process = commandLine.createProcess()
  try {
    return wslIjentBinary to IjentSessionProvider.connect(ijentCoroutineScope, process)
  }
  catch (err: Throwable) {
    try {
      process.destroy()
    }
    catch (err2: Throwable) {
      err.addSuppressed(err)
    }
    throw err
  }
}

private val LOG = Logger.getInstance("com.intellij.platform.ijent.IjentWslLauncher")