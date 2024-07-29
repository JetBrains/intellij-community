// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent

import kotlinx.coroutines.Deferred
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel

/**
 * Represents some process which was launched by IJent via [IjentApi.executeProcess].
 *
 * There are adapters for already written code: [com.intellij.execution.ijent.IjentChildProcessAdapter]
 * and [com.intellij.execution.ijent.IjentChildPtyProcessAdapter].
 */
interface IjentChildProcess {
  val pid: IjentApi.Pid

  /**
   * Although data transmission via this channel could potentially stall due to overflow of [kotlinx.coroutines.channels.Channel],
   * this method does not allow to ensure that a data chunk was actually delivered to the remote process.
   * For synchronous delivery that reports about delivery result, please use [sendStdinWithConfirmation].
   *
   * Note that each chunk of data is individually and immediately flushed into the process without any intermediate buffer storage.
   */
  val stdin: SendChannel<ByteArray>

  val stdout: ReceiveChannel<ByteArray>
  val stderr: ReceiveChannel<ByteArray>
  val exitCode: Deferred<Int>

  /**
   * Sends [data] into the process stdin and waits until the data is received by the process.
   *
   * Notice that every data chunk is flushed into the process separately. There's no buffering.
   */
  @Throws(SendStdinError::class, IjentUnavailableException::class)
  suspend fun sendStdinWithConfirmation(data: ByteArray)

  sealed class SendStdinError(msg: String) : Exception(msg) {
    class ProcessExited : SendStdinError("Process exited")

    /**
     * This error doesn't imply that the process has already exited. It is possible to close the stdin of the process, and the process
     * may live quite a long time after that. However, usually processes exit immediately right after their stdin are closed.
     * Therefore, it may turn out that the process exits at the moment when this error is being observed by the API user.
     */
    class StdinClosed : SendStdinError("Stdin closed")
  }

  /**
   * Sends `SIGINT` on Unix.
   *
   * Does nothing yet on Windows.
   */
  @Throws(IjentUnavailableException::class)
  suspend fun interrupt()

  /**
   * Sends `SIGTERM` on Unix.
   *
   * Calls [`ExitProcess`](https://learn.microsoft.com/en-us/windows/win32/api/processthreadsapi/nf-processthreadsapi-exitprocess) on Windows.
   */
  @Throws(IjentUnavailableException::class)
  suspend fun terminate()

  /**
   * Sends `SIGKILL` on Unix.
   *
   * Calls [`TerminateProcess`](https://learn.microsoft.com/en-us/windows/win32/api/processthreadsapi/nf-processthreadsapi-terminateprocess)
   * on Windows.
   */
  @Throws(IjentUnavailableException::class)
  suspend fun kill()

  @Throws(ResizePtyError::class, IjentUnavailableException::class)  // Can't use @CheckReturnValue: KTIJ-7061
  suspend fun resizePty(columns: Int, rows: Int)

  sealed class ResizePtyError(msg: String) : Exception(msg) {
    class ProcessExited : ResizePtyError("Process exited")
    class NoPty : ResizePtyError("Process has no PTY")
    data class Errno(val errno: Int, override val message: String) : ResizePtyError("[$errno] $message")
  }
}