// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel

/**
 * Methods related to process execution: start a process, collect stdin/stdout/stderr of the process, etc.
 */
interface EelExecApi {
  /**
   * Starts a process on a local or remote machine.
   * stdin, stdout and stderr of the process are always forwarded, if there are.
   *
   * Beware that processes with [ExecuteProcessBuilder.pty] usually don't have stderr.
   * The [EelProcess.stderr] must be an empty stream in such case.
   *
   * By default, environment is always inherited, which may be unwanted. [ExecuteProcessBuilder.env] allows
   * to alter some environment variables, it doesn't clear the variables from the parent. When the process should be started in an
   * environment like in a terminal, the response of [fetchLoginShellEnvVariables] should be put into [ExecuteProcessBuilder.env].
   *
   * All argument, all paths, should be valid for the remote machine. F.i., if the IDE runs on Windows, but IJent runs on Linux,
   * [ExecuteProcessBuilder.workingDirectory] is the path on the Linux host. There's no automatic path mapping in this interface.
   */
  fun executeProcessBuilder(exe: String): ExecuteProcessBuilder


  /**
   * Executes the process, returning either an [EelProcess] or an error provided by the remote operating system.
   *
   * The instance of the [ExecuteProcessBuilder] _may_ become invalid after this call.
   *
   * The method may throw a RuntimeException only in critical cases like connection loss or a bug.
   */
  suspend fun execute(builder: ExecuteProcessBuilder): ExecuteProcessResult

  /** Docs: [executeProcessBuilder] */
  interface ExecuteProcessBuilder {
    fun args(args: List<String>): ExecuteProcessBuilder
    fun env(env: Map<String, String>): ExecuteProcessBuilder
    fun pty(pty: Pty?): ExecuteProcessBuilder
    fun workingDirectory(workingDirectory: String?): ExecuteProcessBuilder


    // TODO: Use path
    val exe: String
    val args: List<String>
    val env: Map<String, String>
    val pty: Pty?
    val workingDirectory: String?

    // API that created this builder
    val api: EelExecApi
  }

  /**
   * Gets the same environment variables on the remote machine as the user would get if they run the shell.
   */
  suspend fun fetchLoginShellEnvVariables(): Map<String, String>

  sealed interface ExecuteProcessResult {
    class Success(val process: EelProcess) : ExecuteProcessResult
    data class Failure(val errno: Int, val message: String) : ExecuteProcessResult
  }

  /** [echo] must be true in general and must be false when the user is asked for a password. */
  data class Pty(val columns: Int, val rows: Int, val echo: Boolean)
}

/** Docs: [EelExecApi.executeProcessBuilder] */
suspend fun EelExecApi.executeProcess(exe: String, vararg args: String): EelExecApi.ExecuteProcessResult =
  executeProcessBuilder(exe).args(listOf(*args)).execute()

/** Docs: [EelExecApi.executeProcessBuilder] */
fun EelExecApi.executeProcessBuilder(exe: String, arg1: String, vararg args: String): EelExecApi.ExecuteProcessBuilder =
  executeProcessBuilder(exe).args(listOf(arg1, *args))

fun EelExecApi.ExecuteProcessBuilder.args(first: String, vararg other: String): EelExecApi.ExecuteProcessBuilder =
  args(listOf(first, *other))

/**
 * See [EelExecApi.execute]
 */
suspend fun EelExecApi.ExecuteProcessBuilder.execute(): EelExecApi.ExecuteProcessResult = api.execute(this)