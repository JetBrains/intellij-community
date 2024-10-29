// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel

import com.intellij.platform.eel.impl.ExecuteProcessBuilderImpl

/**
 * Methods related to process execution: start a process, collect stdin/stdout/stderr of the process, etc.
 */
interface EelExecApi {

  /**
   * Executes the process, returning either an [EelProcess] or an error provided by the remote operating system.
   *
   * The instance of the [ExecuteProcessOptions] _may_ become invalid after this call.
   *
   * The method may throw a RuntimeException only in critical cases like connection loss or a bug.
   *
   * See [executeProcessBuilder]
   */
  suspend fun execute(builder: ExecuteProcessOptions): EelResult<EelProcess, ExecuteProcessError>

  /** Docs: [executeProcessBuilder] */
  interface ExecuteProcessOptions {
    val args: List<String>
    val env: Map<String, String>
    val pty: Pty?
    val workingDirectory: String?

    // TODO: Use EelPath as soon as it will be merged
    val exe: String

    interface Builder {
      fun args(args: List<String>): Builder
      fun env(env: Map<String, String>): Builder
      fun pty(pty: Pty?): Builder
      fun workingDirectory(workingDirectory: String?): Builder
      fun build(): ExecuteProcessOptions
    }

    companion object {
      /**
       * Creates builder to start a process on a local or remote machine.
       * stdin, stdout and stderr of the process are always forwarded, if there are.
       *
       * Beware that processes with [ExecuteProcessOptions.pty] usually don't have stderr.
       * The [EelProcess.stderr] must be an empty stream in such case.
       *
       * By default, environment is always inherited, which may be unwanted. [ExecuteProcessOptions.env] allows
       * to alter some environment variables, it doesn't clear the variables from the parent. When the process should be started in an
       * environment like in a terminal, the response of [fetchLoginShellEnvVariables] should be put into [ExecuteProcessOptions.env].
       *
       * All argument, all paths, should be valid for the remote machine. F.i., if the IDE runs on Windows, but IJent runs on Linux,
       * [ExecuteProcessOptions.workingDirectory] is the path on the Linux host. There's no automatic path mapping in this interface.
       */
      fun Builder(exe: String): Builder = ExecuteProcessBuilderImpl(exe)

      fun Builder(exe: String, arg1: String, vararg args: String): Builder = Builder(exe).args(listOf(arg1, *args))
    }
  }

  /**
   * Gets the same environment variables on the remote machine as the user would get if they run the shell.
   */
  suspend fun fetchLoginShellEnvVariables(): Map<String, String>

  interface ExecuteProcessError {
    val errno: Int
    val message: String
  }

  /** [echo] must be true in general and must be false when the user is asked for a password. */
  data class Pty(val columns: Int, val rows: Int, val echo: Boolean)
}

/** Docs: [EelExecApi.executeProcessBuilder] */
suspend fun EelExecApi.execute(exe: String, setup: (EelExecApi.ExecuteProcessOptions.Builder).() -> Unit): EelResult<EelProcess, EelExecApi.ExecuteProcessError> {
  val builder = EelExecApi.ExecuteProcessOptions.Builder(exe).apply(setup).build()
  return execute(builder)
}

/** Docs: [EelExecApi.executeProcessBuilder] */
suspend fun EelExecApi.executeProcess(exe: String, vararg args: String): EelResult<EelProcess, EelExecApi.ExecuteProcessError> =
  execute(EelExecApi.ExecuteProcessOptions.Builder(exe).args(listOf(*args)).build())

fun EelExecApi.ExecuteProcessOptions.Builder.args(first: String, vararg other: String): EelExecApi.ExecuteProcessOptions.Builder =
  args(listOf(first, *other))
