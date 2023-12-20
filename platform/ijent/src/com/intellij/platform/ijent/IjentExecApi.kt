// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent

import com.intellij.util.SmartList
import org.jetbrains.annotations.ApiStatus

/**
 * Methods related to process execution: start a process, collect stdin/stdout/stderr of the process, etc.
 */
@ApiStatus.Experimental
interface IjentExecApi {
  /**
   * Starts a process on a remote machine. Right now, the child process may outlive the instance of IJent.
   * stdin, stdout and stderr of the process are always forwarded, if there are.
   *
   * Beware that processes with [ExecuteProcessArgs.pty] usually don't have stderr.
   * The [IjentChildProcess.stderr] must be an empty stream in such case.
   *
   * By default, environment is always inherited from the running IJent instance, which may be unwanted. [ExecuteProcessArgs.env] allows
   * to alter some environment variables, it doesn't clear the variables from the parent. When the process should be started in an
   * environment like in a terminal, the response of [fetchLoginShellEnvVariables] should be put into [ExecuteProcessArgs.env].
   *
   * All argument, all paths, should be valid for the remote machine. F.i., if the IDE runs on Windows, but IJent runs on Linux,
   * [ExecuteProcessArgs.workingDirectory] is the path on the Linux host. There's no automatic path mapping in this interface.
   */
  suspend fun executeProcess(args: ExecuteProcessArgs): ExecuteProcessResult

  /** Docs: [executeProcess] */
  class ExecuteProcessArgs(var exe: String) {
    var args: MutableList<String> = SmartList()
    var env: MutableMap<String, String> = HashMap(0)
    var pty: Pty? = null
    var workingDirectory: String? = null

    override fun toString(): String =
      "ExecuteProcessArgs(exe='$exe', args=$args, env=$env, pty=$pty, workingDirectory=$workingDirectory)"
  }

  /**
   * Gets the same environment variables on the remote machine as the user would get if they run the shell.
   */
  suspend fun fetchLoginShellEnvVariables(): Map<String, String>

  sealed interface ExecuteProcessResult {
    class Success(val process: IjentChildProcess) : ExecuteProcessResult
    data class Failure(val errno: Int, val message: String) : ExecuteProcessResult
  }

  /** [echo] must be true in general and must be false when the user is asked for a password. */
  data class Pty(val columns: Int, val rows: Int, val echo: Boolean)
}

/** Docs: [IjentExecApi.executeProcess] */
suspend fun IjentExecApi.executeProcess(
  exe: String,
  vararg args: String,
  builder: IjentExecApi.ExecuteProcessArgs.() -> Unit = {},
): IjentExecApi.ExecuteProcessResult {
  require(exe.isNotEmpty()) { "Executable must be specified" }
  return executeProcess(IjentExecApi.ExecuteProcessArgs(exe).apply {
    this.args += args
    builder()
  })
}