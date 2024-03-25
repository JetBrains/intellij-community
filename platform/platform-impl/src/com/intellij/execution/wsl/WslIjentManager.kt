// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.wsl

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.IntellijInternalApi
import com.intellij.platform.ijent.IjentApi
import com.intellij.platform.ijent.bootstrapOverShellSession
import com.intellij.util.io.computeDetached
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.VisibleForTesting

/**
 * An entry point for running [IjentApi] over WSL and checking if [IjentApi] even should be used for WSL.
 */
@ApiStatus.Experimental
interface WslIjentManager {
  /**
   * This coroutine scope is for usage in [com.intellij.execution.ijent.IjentChildProcessAdapter] and things like that.
   */
  @DelicateCoroutinesApi
  val processAdapterScope: CoroutineScope

  /**
   * The returned instance is not supposed to be closed by the caller. [WslIjentManager] closes [IjentApi] by itself during shutdown.
   */
  suspend fun getIjentApi(wslDistribution: WSLDistribution, project: Project?, rootUser: Boolean): IjentApi

  @get:ApiStatus.Internal
  val isIjentAvailable: Boolean

  companion object {
    @JvmStatic
    fun getInstance(): WslIjentManager = service()
  }
}

suspend fun deployAndLaunchIjent(
  project: Project?,
  wslDistribution: WSLDistribution,
  wslCommandLineOptionsModifier: (WSLCommandLineOptions) -> Unit = {},
): IjentApi = deployAndLaunchIjentGettingPath(project, wslDistribution, wslCommandLineOptionsModifier).second

@OptIn(IntellijInternalApi::class, DelicateCoroutinesApi::class)
@VisibleForTesting
suspend fun deployAndLaunchIjentGettingPath(
  project: Project?,
  wslDistribution: WSLDistribution,
  wslCommandLineOptionsModifier: (WSLCommandLineOptions) -> Unit = {},
): Pair<String, IjentApi> {
  // IJent can start an interactive shell by itself whenever it needs.
  // Enabling an interactive shell for IJent by default can bring problems, because stdio of IJent must not be populated
  // with possible user extensions in ~/.profile
  val wslCommandLineOptions = WSLCommandLineOptions()
    .setExecuteCommandInInteractiveShell(false)
    .setExecuteCommandInLoginShell(false)
    .setExecuteCommandInShell(false)

  wslCommandLineOptionsModifier(wslCommandLineOptions)

  val commandLine = WSLDistribution.neverRunTTYFix(GeneralCommandLine("/bin/sh"))
  wslDistribution.doPatchCommandLine(commandLine, project, wslCommandLineOptions)

  val process = computeDetached { commandLine.createProcess() }
  return bootstrapOverShellSession("WSL-${wslDistribution.id}", process, wslDistribution::getWslPath)
}