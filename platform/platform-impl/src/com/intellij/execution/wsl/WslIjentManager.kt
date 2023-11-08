// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.wsl

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.UnixSignal
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
import com.intellij.util.channel.ChannelInputStream
import com.intellij.util.channel.ChannelOutputStream
import com.intellij.util.suspendingLazy
import com.jetbrains.rd.util.concurrentMapOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import org.jetbrains.annotations.VisibleForTesting
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.io.path.absolutePathString

@Service
class WslIjentManager private constructor(private val scope: CoroutineScope) {
  private val myCache: MutableMap<String, SuspendingLazy<IjentApi>> = concurrentMapOf()

  suspend fun getIjentApi(wslDistribution: WSLDistribution, project: Project?, rootUser: Boolean): IjentApi {
    return myCache.computeIfAbsent(wslDistribution.id + if (rootUser) ":root" else "") {
      scope.suspendingLazy {
        deployAndLaunchIjent(scope, project, wslDistribution, WSLCommandLineOptions().setSudo(rootUser))
      }
    }.getValue()
  }

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

      when (val processResult = getIjentApi(wslDistribution, project, options.isSudo).executeProcess(
        exe = FileUtil.toSystemIndependentName(command.first()),
        args = *command.toList().drop(1).toTypedArray(),
        env = processBuilder.environment(),
        pty = pty,
        workingDirectory = processBuilder.directory()?.let { wslDistribution.getWslPath(it.toPath()) }
      )) {
        is IjentApi.ExecuteProcessResult.Success -> processResult.process.toProcess(pty != null)
        is IjentApi.ExecuteProcessResult.Failure -> throw IOException(processResult.message)
      }
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
  }
}

@OptIn(ExperimentalCoroutinesApi::class)
private fun IjentChildProcess.toProcess(isPty: Boolean): Process {
  return object : Process() {
    private val myIsDestroyed = AtomicBoolean(false)
    private val myOutStream by lazy { ChannelOutputStream(stdin) }
    private val myInputStream by lazy { ChannelInputStream(stdout) }
    private val myErrorStream by lazy { if (isPty) ByteArrayInputStream(byteArrayOf()) else ChannelInputStream(stderr) }

    override fun waitFor(): Int {
      return try {
        runBlocking {
          exitCode.await()
        }
      }
      catch (e: Throwable) {
        when (e) {
          is CancellationException -> throw InterruptedException()
          else -> throw RuntimeException(e)
        }
      }
    }

    override fun getOutputStream(): OutputStream = myOutStream
    override fun getInputStream(): InputStream = myInputStream
    override fun getErrorStream(): InputStream = myErrorStream

    override fun destroy() {
      if (myIsDestroyed.compareAndSet(false, true)) {
        runBlocking {
          sendSignal(UnixSignal.SIGTERM.linuxCode)
        }

        myOutStream.close()
        myInputStream.close()
        myErrorStream.close()
      }
    }

    override fun exitValue(): Int {
      return if (exitCode.isCompleted) exitCode.getCompleted() else -1
    }
  }
}

@VisibleForTesting
suspend fun deployAndLaunchIjent(
  ijentCoroutineScope: CoroutineScope,
  project: Project?,
  wslDistribution: WSLDistribution,
  wslCommandLineOptions: WSLCommandLineOptions = WSLCommandLineOptions(),
): IjentApi = deployAndLaunchIjentGettingPath(ijentCoroutineScope, project, wslDistribution, wslCommandLineOptions).second

@VisibleForTesting
suspend fun deployAndLaunchIjentGettingPath(
  ijentCoroutineScope: CoroutineScope,
  project: Project?,
  wslDistribution: WSLDistribution,
  wslCommandLineOptions: WSLCommandLineOptions = WSLCommandLineOptions(),
): Pair<String, IjentApi> {
  val targetPlatform = IjentExecFileProvider.SupportedPlatform.X86_64__LINUX
  val ijentBinary = IjentExecFileProvider.getIjentBinary(targetPlatform)

  val wslIjentBinary = wslDistribution.getWslPath(ijentBinary.toAbsolutePath())!!

  val commandLine = GeneralCommandLine(
    // It's supposed that WslDistribution always converts commands into SHELL.
    // There's no strict reason to call 'exec', just a tiny optimization.
    listOf("exec") +
    getIjentGrpcArgv(wslIjentBinary)
  )
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