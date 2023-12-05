// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent

import org.jetbrains.annotations.ApiStatus

/**
 * Methods related to process execution: start a process, collect stdin/stdout/stderr of the process, etc.
 */
@ApiStatus.Experimental
interface IjentExecApi {
  val ijentApi: IjentApi

  /**
   * Starts a process on a remote machine. Right now, the child process may outlive the instance of IJent.
   * stdin, stdout and stderr of the process are always forwarded, if there are.
   *
   * Beware that processes with [pty] usually don't have stderr. The [IjentChildProcess.stderr] must be an empty stream in such case.
   *
   * By default, environment is always inherited from the running IJent instance, which may be unwanted. [env] allows to alter
   * some environment variables, it doesn't clear the variables from the parent. When the process should be started in an environment like
   * in a terminal, the response of [fetchLoginShellEnvVariables] should be put into [env].
   *
   * All argument, all paths, should be valid for the remote machine. F.i., if the IDE runs on Windows, but IJent runs on Linux,
   * [workingDirectory] is the path on the Linux host. There's no automatic path mapping in this interface.
   */
  suspend fun executeProcess(
    exe: String,
    vararg args: String,
    env: Map<String, String> = emptyMap(),
    pty: Pty? = null,
    workingDirectory: String? = null,
  ): ExecuteProcessResult

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
