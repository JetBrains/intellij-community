// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent

import com.intellij.platform.eel.EelExecApi

  // TODO Extract into a separate interface, like IjentFileSystemApi.Arguments
  /**
   * Starts a process on a remote machine. Right now, the child process may outlive the instance of IJent.
   * stdin, stdout and stderr of the process are always forwarded, if there are.
   *
   * Beware that processes with [ExecuteProcessOptions.pty] usually don't have stderr.
   * The [IjentChildProcess.stderr] must be an empty stream in such case.
   *
   * By default, environment is always inherited from the running IJent instance, which may be unwanted. [ExecuteProcessOptions.env] allows
   * to alter some environment variables, it doesn't clear the variables from the parent. When the process should be started in an
   * environment like in a terminal, the response of [fetchLoginShellEnvVariables] should be put into [ExecuteProcessOptions.env].
   *
   * All argument, all paths, should be valid for the remote machine. F.i., if the IDE runs on Windows, but IJent runs on Linux,
   * [ExecuteProcessOptions.workingDirectory] is the path on the Linux host. There's no automatic path mapping in this interface.
   */
interface IjentExecApi: EelExecApi
