// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel

interface KillableProcess {
  /**
   * Sends `SIGINT` on Unix.
   *
   * Sends `CTRL+C` on Windows (by attaching console).
   *
   * Warning: This signal could be ignored!
   */
  suspend fun interrupt()

  /**
   * Sends `SIGTERM` on Unix.
   *
   * Calls [`ExitProcess`](https://learn.microsoft.com/en-us/windows/win32/api/processthreadsapi/nf-processthreadsapi-exitprocess) on Windows.
   */
  suspend fun terminate()

  /**
   * Sends `SIGKILL` on Unix.
   *
   * Calls [`TerminateProcess`](https://learn.microsoft.com/en-us/windows/win32/api/processthreadsapi/nf-processthreadsapi-terminateprocess)
   * on Windows.
   */
  suspend fun kill()

}