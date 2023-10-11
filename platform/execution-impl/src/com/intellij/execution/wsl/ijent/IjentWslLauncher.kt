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
import com.intellij.platform.ijent.getIjentGrpcArgv
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
  val targetPlatform = IjentExecFileProvider.SupportedPlatform.X86_64__LINUX
  val ijentBinary = IjentExecFileProvider.getIjentBinary(targetPlatform)

  val wslIjentBinary = wslDistribution.getWslPath(ijentBinary.absolutePathString())!!

  val commandLine = GeneralCommandLine(
    // It's supposed that WslDistribution always converts commands into SHELL.
    // There's no strict reason to call 'exec', just a tiny optimization.
    listOf("exec") +
    getIjentGrpcArgv(wslIjentBinary)
  )
  wslDistribution.patchCommandLine(commandLine, project, wslCommandLineOptions)

  LOG.debug {
    "Going to launch IJent: ${commandLine.commandLineString}"
  }

  val process = commandLine.createProcess()
  try {
    return wslIjentBinary to IjentSessionProvider.connect(ijentCoroutineScope, targetPlatform, process)
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