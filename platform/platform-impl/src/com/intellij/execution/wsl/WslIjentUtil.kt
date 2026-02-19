// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("WslIjentUtil")
@file:Suppress("RAW_RUN_BLOCKING")  // These functions are called by different legacy code, a ProgressIndicator is not always available.
@file:ApiStatus.Internal

package com.intellij.execution.wsl

import com.intellij.execution.CommandLineUtil.posixQuote
import com.intellij.execution.ijent.IjentChildProcessAdapter
import com.intellij.execution.ijent.IjentChildPtyProcessAdapter
import com.intellij.execution.process.LocalPtyOptions
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.openapi.project.Project
import com.intellij.platform.eel.EelExecApi
import com.intellij.platform.eel.EelExecPosixApi
import com.intellij.platform.eel.EelProcess
import com.intellij.platform.eel.environmentVariables
import com.intellij.platform.eel.path.EelPath
import com.intellij.platform.eel.spawnProcess
import com.intellij.platform.ijent.IjentPosixApi
import com.intellij.util.PathUtil
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresBlockingContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import org.jetbrains.annotations.ApiStatus

/**
 * An adapter for [com.intellij.platform.ijent.IjentExecApi.fetchLoginShellEnvVariables] for Java.
 */
@RequiresBackgroundThread
@RequiresBlockingContext
fun fetchLoginShellEnv(
  wslIjentManager: WslIjentManager,
  wslDistribution: WSLDistribution,
  project: Project?,
  rootUser: Boolean,
): Map<String, String> =
  runBlockingMaybeCancellable {
    wslIjentManager.getIjentApi(null, wslDistribution, project, rootUser).exec.fetchLoginShellEnvVariables()
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
 * [ProcessBuilder.directory] is a Windows path, and the constructor of [java.io.File] can corrupt the path. Therefore,
 * [WSLCommandLineOptions.getRemoteWorkingDirectory] is preferred over [ProcessBuilder.directory].
 */
@RequiresBackgroundThread
@RequiresBlockingContext
fun runProcessBlocking(
  wslIjentManager: WslIjentManager,
  project: Project?,
  wslDistribution: WSLDistribution,
  processBuilder: ProcessBuilder,
  options: WSLCommandLineOptions,
  ptyOptions: LocalPtyOptions?,
): Process = runBlockingCancellable {
  val ijentApi: IjentPosixApi = wslIjentManager.getIjentApi(null, wslDistribution, project, options.isSudo)

  val (command, envs) = applyWslOptions(processBuilder.command(), processBuilder.environment(), ijentApi.exec, options)

  val interactionOptions = when {
    ptyOptions != null -> with(ptyOptions) { EelExecApi.Pty(initialColumns, initialRows, !consoleMode) }
    processBuilder.redirectErrorStream() -> EelExecApi.RedirectStdErr(to = EelExecApi.RedirectTo.STDOUT)
    else -> null
  }

  val workingDirectory = processBuilder.directory()?.toPath()?.let { windowsWorkingDirectory ->
    wslDistribution.getWslPath(windowsWorkingDirectory)
    ?: run {
      LOG.warn("Working directory $windowsWorkingDirectory can't be mapped to WSL distribution ${wslDistribution.id}", Throwable())
      null
    }
  }

  val scope = @OptIn(DelicateCoroutinesApi::class) (wslIjentManager.processAdapterScope)
  ijentApi.exec.spawnProcess(PathUtil.toSystemIndependentName(command.first()))
    .args(command.drop(1))
    .env(envs)
    .interactionOptions(interactionOptions)
    .workingDirectory(workingDirectory?.let { EelPath.parse(it, ijentApi.descriptor) })
    .eelIt()
    .toProcess(
      coroutineScope = scope,
      isPty = interactionOptions != null,
    )
}

/**
 * Applies [options] to given [command] and [envs] and
 * returns new command and new envs which can be used without the [options].
 *
 * Modifications of [command] is performed if any of the following happens:
 * * `options.isExecuteCommandInShell` is true
 * * `options.initShellCommands` is not empty
 * * `options.remoteWorkingDirectory` is not empty
 *
 * In this case, [command] is executed in shell and the shell mode (login/interactive) is determined by
 * `options.isExecuteCommandInLoginShell` / `options.isExecuteCommandInInteractiveShell`.
 *
 * @param command the original command to be executed
 * @param envs the original environment variables
 * @param execApi an instance of the [EelExecPosixApi] interface
 * @param options an instance of [WSLCommandLineOptions] specifying how to modify the command and environment variables
 * @return a pair containing the modified command and environment variables
 */
@ApiStatus.Internal
suspend fun applyWslOptions(
  command: List<String>,
  envs: Map<String, String>,
  execApi: EelExecPosixApi,
  options: WSLCommandLineOptions,
): Pair<List<String>, Map<String, String>> {
  require(!options.isExecuteCommandInDefaultShell) {
    "This API is not supposed to handle WSLCommandLineOptions.isExecuteCommandInDefaultShell"
  }

  val shellInitCommands = options.initShellCommands.asReversed().toMutableList()

  val resultEnvs = if (options.isExecuteCommandInShell && !options.isPassEnvVarsUsingInterop) {
    for ((name, value) in envs.entries.sortedBy { it.key }) {
      if (WSLDistribution.ENV_VARIABLE_NAME_PATTERN.matcher(name).matches()) {
        shellInitCommands += "export ${posixQuote(name)}=${posixQuote(value)}"
      }
      else {
        LOG.debug { "Can not pass environment variable (bad name): '$name'" }
      }
    }
    emptyMap()
  }
  else {
    envs
  }

  options.remoteWorkingDirectory?.takeIf(String::isNotEmpty)?.let { remoteWorkingDirectory ->
    // Although there's another and more straightforward way to specify the working directory, this code repeats the logic from
    // `WSLDistribution.doPatchCommandLine`, just to not break someone's workflow.
    shellInitCommands += "cd ${posixQuote(remoteWorkingDirectory)}"
  }

  val resultCommand = if (options.isExecuteCommandInShell || shellInitCommands.isNotEmpty()) {
    // The sequence of the argument should correspond to the sequence from `com.intellij.execution.wsl.WSLDistribution#doPatchCommandLine`
    // Although some argument may be mixed up de-facto, the function is covered by unit tests that check arguments using strict comparison.
    val commandArgument = (shellInitCommands + command.joinToString(" ", transform = ::posixQuote)).joinToString(" && ")
    buildList {
      add(getShellEnvVar(execApi))
      if (options.isExecuteCommandInInteractiveShell) {
        add("-i")
      }
      if (options.isExecuteCommandInLoginShell) {
        add("-l")
      }
      add("-c")
      add(commandArgument)
    }
  }
  else {
    command
  }
  return resultCommand to resultEnvs
}

private suspend fun getShellEnvVar(exec: EelExecPosixApi): String {
  // It's a happy coincidence that WSL minimal environment variables contains SHELL!
  @Suppress("checkedExceptions") // EnvironmentVariablesException is not thrown in minimal mode
  val parentEnvs = exec.environmentVariables().minimal().eelIt().await()
  return parentEnvs["SHELL"] ?: WSLDistribution.DEFAULT_SHELL
}

private fun EelProcess.toProcess(
  coroutineScope: CoroutineScope,
  isPty: Boolean,
): Process =
  if (isPty)
    IjentChildPtyProcessAdapter(coroutineScope, this)
  else
    IjentChildProcessAdapter(coroutineScope, this)

private val LOG by lazy { Logger.getInstance("com.intellij.execution.wsl.WslIjentUtil") }