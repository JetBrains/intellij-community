// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("WslIjentJavaUtil")
@file:Suppress("RAW_RUN_BLOCKING")  // These functions are called by different legacy code, a ProgressIndicator is not always available.
package com.intellij.execution.wsl

import com.intellij.execution.ijent.IjentChildProcessAdapter
import com.intellij.execution.ijent.IjentChildPtyProcessAdapter
import com.intellij.execution.process.LocalPtyOptions
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.platform.ijent.IjentChildProcess
import com.intellij.platform.ijent.IjentExecApi
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresBlockingContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.runBlocking
import java.io.IOException

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
  runBlocking {
    wslIjentManager.getIjentApi(wslDistribution, project, rootUser).exec.fetchLoginShellEnvVariables()
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
): Process = runBlocking {
  val command = processBuilder.command()
  val directory =
    options.remoteWorkingDirectory
    ?: processBuilder.directory()?.let { wslDistribution.getWslPath(it.toPath()) }

  val pty = ptyOptions?.run {
    IjentExecApi.Pty(initialColumns, initialRows, !consoleMode)
  }

  val ijentApi = wslIjentManager.getIjentApi(wslDistribution, project, options.isSudo)
  val scope = @OptIn(DelicateCoroutinesApi::class) (wslIjentManager.processAdapterScope)
  when (val processResult = ijentApi.exec.executeProcessBuilder(FileUtil.toSystemIndependentName(command.first()))
    .args(command.toList().drop(1))
    .env(processBuilder.environment())
    .pty(pty)
    .workingDirectory(directory)
    .execute()
  ) {
    is IjentExecApi.ExecuteProcessResult.Success -> processResult.process.toProcess(scope, pty != null)
    is IjentExecApi.ExecuteProcessResult.Failure -> throw IOException(processResult.message)
  }
}

private fun IjentChildProcess.toProcess(coroutineScope: CoroutineScope, isPty: Boolean): Process =
  if (isPty)
    IjentChildPtyProcessAdapter(coroutineScope, this)
  else
    IjentChildProcessAdapter(coroutineScope, this)
