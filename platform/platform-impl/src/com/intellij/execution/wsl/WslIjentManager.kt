// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.wsl

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.ijent.IjentChildProcessAdapter
import com.intellij.execution.ijent.IjentChildPtyProcessAdapter
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.IntellijInternalApi
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.ijent.*
import com.intellij.platform.util.coroutines.namedChildScope
import com.intellij.util.SuspendingLazy
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresBlockingContext
import com.intellij.util.io.computeDetached
import com.intellij.util.suspendingLazy
import com.jetbrains.rd.util.concurrentMapOf
import kotlinx.coroutines.*
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import org.jetbrains.annotations.VisibleForTesting
import java.io.IOException

/**
 * An entry point for running [IjentApi] over WSL and checking if [IjentApi] even should be used for WSL.
 */
@ApiStatus.Experimental
@Service
@Suppress("RAW_RUN_BLOCKING")  // This class is called by different legacy code, a ProgressIndicator is not always available.
class WslIjentManager private constructor(private val scope: CoroutineScope) {
  private val myCache: MutableMap<String, SuspendingLazy<IjentApi>> = concurrentMapOf()

  /**
   * The returned instance is not supposed to be closed by the caller. [WslIjentManager] closes [IjentApi] by itself during shutdown.
   */
  suspend fun getIjentApi(wslDistribution: WSLDistribution, project: Project?, rootUser: Boolean): IjentApi {
    return myCache.compute(wslDistribution.id + if (rootUser) ":root" else "") { _, oldHolder ->
      val validOldHolder = when (oldHolder?.isInitialized()) {
        true ->
          if (oldHolder.getInitialized().isRunning()) oldHolder
          else null
        false -> oldHolder
        null -> null
      }

      validOldHolder ?: scope.suspendingLazy {
        val scopeName = "IJent on WSL $wslDistribution"
        val ijentScope = scope.namedChildScope(scopeName, CoroutineExceptionHandler { _, err ->
          LOG.error("Unexpected error in $scopeName", err)
        })
        deployAndLaunchIjent(ijentScope, project, wslDistribution, wslCommandLineOptionsModifier = { it.setSudo(rootUser) })
      }
    }!!.getValue()
  }

  @RequiresBackgroundThread
  @RequiresBlockingContext
  fun fetchLoginShellEnv(wslDistribution: WSLDistribution, project: Project?, rootUser: Boolean): Map<String, String> {
    return runBlocking {
      getIjentApi(wslDistribution, project, rootUser).exec.fetchLoginShellEnvVariables()
    }
  }

  /**
   * Runs a process inside a WSL container defined by [wslDistribution] using IJent.
   *
   * [project] is supposed to be used only for showing notifications with appropriate modality. Therefore, it may be almost safely omitted.
   *
   * [processBuilder] chosen as a convenient adapter for already written code. The functions uses command line arguments, environment
   * variables and the working directory defined by [processBuilder].
   *
   * The function ignores [ProcessBuilder.redirectInput], [ProcessBuilder.redirectOutput], [ProcessBuilder.redirectError] and similar
   * methods. Stdin, stdout, and stderr are always piped. The caller MUST drain both [Process.getInputStream] and [Process.getErrorStream].
   * Otherwise, the remote operating system may suspend the remote process due to buffer overflow.
   *
   * [isSudo] allows to start IJent inside WSL through sudo. It implies that all children processes, including [processBuilder] will run
   * from the root user as well.
   */
  @RequiresBackgroundThread
  @RequiresBlockingContext
  fun runProcessBlocking(
    wslDistribution: WSLDistribution,
    project: Project?,
    processBuilder: ProcessBuilder,
    pty: IjentExecApi.Pty?,
    isSudo: Boolean,
  ): Process {
    return runBlocking {
      val command = processBuilder.command()

      val ijentApi = getIjentApi(wslDistribution, project, isSudo)
      when (val processResult = ijentApi.exec.executeProcess(FileUtil.toSystemIndependentName(command.first())) {
        args += command.toList().drop(1)
        env += processBuilder.environment()
        this.pty = pty
        workingDirectory = processBuilder.directory()?.let { wslDistribution.getWslPath(it.toPath()) }
      }) {
        is IjentExecApi.ExecuteProcessResult.Success -> processResult.process.toProcess(scope, pty != null)
        is IjentExecApi.ExecuteProcessResult.Failure -> throw IOException(processResult.message)
      }
    }
  }

  @VisibleForTesting
  fun dropCache() {
    myCache.values.removeAll { ijent ->
      if (ijent.isInitialized()) {
        ijent.getInitialized().close()
      }
      true
    }
  }

  companion object {
    private val LOG = logger<WslIjentManager>()

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

private fun IjentChildProcess.toProcess(coroutineScope: CoroutineScope, isPty: Boolean): Process =
  if (isPty)
    IjentChildPtyProcessAdapter(coroutineScope, this)
  else
    IjentChildProcessAdapter(coroutineScope, this)

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