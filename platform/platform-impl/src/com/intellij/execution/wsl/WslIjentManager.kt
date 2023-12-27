// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.wsl

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.components.service
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.IntellijInternalApi
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.ijent.IjentApi
import com.intellij.platform.ijent.IjentSessionProvider
import com.intellij.util.io.computeDetached
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
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

  companion object {
    @JvmStatic
    fun isIjentAvailable(): Boolean {
      val id = PluginId.getId("intellij.platform.ijent.impl")
      return Registry.`is`("wsl.use.remote.agent.for.launch.processes", false) && PluginManagerCore.getPlugin(id)?.isEnabled == true
    }

    @JvmStatic
    fun getInstance(): WslIjentManager = service()

    @TestOnly
    @JvmStatic
    fun overrideIsIjentAvailable(value: Boolean): AutoCloseable {
      val registry = Registry.get("wsl.use.remote.agent.for.launch.processes")
      registry.setValue(value)
      return AutoCloseable {
        registry.resetToDefault()
      }
    }
  }
}

suspend fun deployAndLaunchIjent(
  ijentCoroutineScope: CoroutineScope,
  project: Project?,
  wslDistribution: WSLDistribution,
  wslCommandLineOptionsModifier: (WSLCommandLineOptions) -> Unit = {},
): IjentApi = deployAndLaunchIjentGettingPath(ijentCoroutineScope, project, wslDistribution, wslCommandLineOptionsModifier).second

@OptIn(IntellijInternalApi::class, DelicateCoroutinesApi::class)
@VisibleForTesting
suspend fun deployAndLaunchIjentGettingPath(
  ijentCoroutineScope: CoroutineScope,
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
  return IjentSessionProvider.bootstrapOverShellSession(ijentCoroutineScope, process)
}