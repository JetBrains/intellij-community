// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent

import kotlinx.coroutines.Deferred
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import org.jetbrains.annotations.ApiStatus

/**
 * Represents some process which was launched by IJent via [IjentApi.executeProcess].
 *
 * There are adapters for already written code: [com.intellij.execution.ijent.IjentChildProcessAdapter]
 * and [com.intellij.execution.ijent.IjentChildPtyProcessAdapter].
 */
@ApiStatus.Experimental
interface IjentChildProcess : AutoCloseable {
  val pid: Int

  /**
   * Although sending data through this channel may block due to buffer overflows, this method doesn't wait for actual delivery
   * of the data to the process.
   * For synchronous delivery [sendStdinWithConfirmation] should be used.
   *
   * Notice that every data chunk is flushed into the process separately. There's no buffering.
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
  @Throws(SendStdinError::class)
  suspend fun sendStdinWithConfirmation(data: ByteArray)

  sealed class SendStdinError(msg: String) : Exception(msg) {
    class ProcessExited : SendStdinError("Process exited")
  }

  @Deprecated("Switch either to kill or terminate")
  suspend fun sendSignal(signal: Int)

  suspend fun terminate()
  suspend fun kill()

  @Throws(ResizePtyError::class)  // Can't use @CheckReturnValue: KTIJ-7061
  suspend fun resizePty(columns: Int, rows: Int)

  @Deprecated("No need in explicit deinitialization anymore")
  override fun close(): Unit = Unit

  sealed class ResizePtyError(msg: String) : Exception(msg) {
    class ProcessExited : ResizePtyError("Process exited")
    class NoPty : ResizePtyError("Process has no PTY")
    data class Errno(val errno: Int, override val message: String) : ResizePtyError("[$errno] $message")
  }
}