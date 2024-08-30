// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent

/**
 * Methods related to process execution: start a process, collect stdin/stdout/stderr of the process, etc.
 */
interface IjentExecApi {
  // TODO Extract into a separate interface, like IjentFileSystemApi.Arguments
  /**
   * Starts a process on a remote machine. Right now, the child process may outlive the instance of IJent.
   * stdin, stdout and stderr of the process are always forwarded, if there are.
   *
   * Beware that processes with [ExecuteProcessBuilder.pty] usually don't have stderr.
   * The [IjentChildProcess.stderr] must be an empty stream in such case.
   *
   * By default, environment is always inherited from the running IJent instance, which may be unwanted. [ExecuteProcessBuilder.env] allows
   * to alter some environment variables, it doesn't clear the variables from the parent. When the process should be started in an
   * environment like in a terminal, the response of [fetchLoginShellEnvVariables] should be put into [ExecuteProcessBuilder.env].
   *
   * All argument, all paths, should be valid for the remote machine. F.i., if the IDE runs on Windows, but IJent runs on Linux,
   * [ExecuteProcessBuilder.workingDirectory] is the path on the Linux host. There's no automatic path mapping in this interface.
   */
  fun executeProcessBuilder(exe: String): ExecuteProcessBuilder

  /** Docs: [executeProcessBuilder] */
  interface ExecuteProcessBuilder {
    fun args(args: List<String>): ExecuteProcessBuilder
    fun env(env: Map<String, String>): ExecuteProcessBuilder
    fun pty(pty: Pty?): ExecuteProcessBuilder
    fun workingDirectory(workingDirectory: String?): ExecuteProcessBuilder

    /**
     * Executes the process, returning either an [IjentChildProcess] or an error provided by the remote operating system.
     *
     * The instance of the [ExecuteProcessBuilder] _may_ become invalid after this call.
     *
     * The method may throw a RuntimeException only in critical cases like connection loss or a bug.
     */
    suspend fun execute(): ExecuteProcessResult
  }

  /**
   * Gets the same environment variables on the remote machine as the user would get if they run the shell.
   */
  @Throws(IjentUnavailableException::class)
  suspend fun fetchLoginShellEnvVariables(): Map<String, String>

  sealed interface ExecuteProcessResult {
    class Success(val process: IjentChildProcess) : ExecuteProcessResult
    data class Failure(val errno: Int, val message: String) : ExecuteProcessResult
  }

  /** [echo] must be true in general and must be false when the user is asked for a password. */
  data class Pty(val columns: Int, val rows: Int, val echo: Boolean)
}

/** Docs: [IjentExecApi.executeProcessBuilder] */
@Throws(IjentUnavailableException::class)
suspend fun IjentExecApi.executeProcess(exe: String, vararg args: String): IjentExecApi.ExecuteProcessResult =
  executeProcessBuilder(exe).args(listOf(*args)).execute()

/** Docs: [IjentExecApi.executeProcessBuilder] */
fun IjentExecApi.executeProcessBuilder(exe: String, arg1: String, vararg args: String): IjentExecApi.ExecuteProcessBuilder =
  executeProcessBuilder(exe).args(listOf(arg1, *args))

fun IjentExecApi.ExecuteProcessBuilder.args(first: String, vararg other: String): IjentExecApi.ExecuteProcessBuilder =
  args(listOf(first, *other))