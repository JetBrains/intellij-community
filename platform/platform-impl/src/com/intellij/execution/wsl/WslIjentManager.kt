// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.wsl

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.ijent.IjentChildProcessAdapter
import com.intellij.execution.ijent.IjentChildPtyProcessAdapter
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.ijent.*
import com.intellij.util.SuspendingLazy
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresBlockingContext
import com.intellij.util.suspendingLazy
import com.jetbrains.rd.util.concurrentMapOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.runBlocking
import org.jetbrains.annotations.TestOnly
import org.jetbrains.annotations.VisibleForTesting
import java.io.IOException

@Service
class WslIjentManager private constructor(private val scope: CoroutineScope) {
  private val myCache: MutableMap<String, SuspendingLazy<IjentApi>> = concurrentMapOf()

  /**
   * The returned instance is not supposed to be closed by the caller. [WslIjentManager] closes [IjentApi] by itself during shutdown.
   */
  suspend fun getIjentApi(wslDistribution: WSLDistribution, project: Project?, rootUser: Boolean): IjentApi {
    return myCache.compute(wslDistribution.id + if (rootUser) ":root" else "") { _, oldHolder ->
      val validOldHolder = when (oldHolder?.isInitialized()) {
        true ->
          if (oldHolder.getInitialized().coroutineScope.isActive) oldHolder
          else null
        false -> oldHolder
        null -> null
      }

      validOldHolder ?: scope.suspendingLazy {
        deployAndLaunchIjent(scope, project, wslDistribution, wslCommandLineOptionsModifier = { it.setSudo(rootUser) })
      }
    }!!.getValue()
  }

  @RequiresBackgroundThread
  @RequiresBlockingContext
  fun fetchLoginShellEnv(wslDistribution: WSLDistribution, project: Project?, rootUser: Boolean): Map<String, String> {
    return runBlocking {
      getIjentApi(wslDistribution, project, rootUser).fetchLoginShellEnvVariables()
    }
  }

  fun runProcessBlocking(
    wslDistribution: WSLDistribution,
    project: Project?,
    processBuilder: ProcessBuilder,
    options: WSLCommandLineOptions,
    pty: IjentApi.Pty?
  ): Process {
    return runBlocking {
      val command = processBuilder.command()

      val ijentApi = getIjentApi(wslDistribution, project, options.isSudo)
      when (val processResult = ijentApi.executeProcess(
        exe = FileUtil.toSystemIndependentName(command.first()),
        args = *command.toList().drop(1).toTypedArray(),
        env = processBuilder.environment(),
        pty = pty,
        workingDirectory = processBuilder.directory()?.let { wslDistribution.getWslPath(it.toPath()) }
      )) {
        is IjentApi.ExecuteProcessResult.Success -> processResult.process.toProcess(ijentApi.coroutineScope, pty != null)
        is IjentApi.ExecuteProcessResult.Failure -> throw IOException(processResult.message)
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
    @JvmStatic
    fun isIjentAvailable(): Boolean {
      val id = PluginId.getId("intellij.platform.ijent.impl")
      return Registry.`is`("wsl.use.remote.agent.for.launch.processes") && PluginManagerCore.getPlugin(id)?.isEnabled == true
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

  wslCommandLineOptionsModifier(wslCommandLineOptions)

  val targetPlatform = IjentExecFileProvider.SupportedPlatform.X86_64__LINUX
  val ijentBinary = IjentExecFileProvider.getIjentBinary(targetPlatform)

  val wslIjentBinary = wslDistribution.getWslPath(ijentBinary.toAbsolutePath())!!

  val commandLine = WSLDistribution.neverRunTTYFix(GeneralCommandLine(
    // It's supposed that WslDistribution always converts commands into SHELL.
    // There's no strict reason to call 'exec', just a tiny optimization.
    listOf("exec") +
    getIjentGrpcArgv(wslIjentBinary)
  ))
  wslDistribution.doPatchCommandLine(commandLine, project, wslCommandLineOptions)

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