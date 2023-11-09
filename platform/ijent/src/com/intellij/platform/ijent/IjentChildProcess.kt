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
  val stdin: SendChannel<ByteArray>
  val stdout: ReceiveChannel<ByteArray>
  val stderr: ReceiveChannel<ByteArray>
  val exitCode: Deferred<Int>

  @Deprecated("Switch either to kill or terminate")
  suspend fun sendSignal(signal: Int)

  suspend fun terminate()
  suspend fun kill()

  @Throws(ResizePtyError::class)  // Can't use @CheckReturnValue: KTIJ-7061
  suspend fun resizePty(columns: Int, rows: Int)

  /**
   * This method should return immediately and produce no errors. The actual destruction should happen in background. Errors may be logged.
   * The method must be idempotent.
   *
   * It is safe to call the method even if the process hasn't exited. However, all further calls to other methods of the interface will fail
   * in that case.
   *
   * If it is cumbersome to call [close] explicitly, [AutoClosingIjentChildProcess] may be useful.
   */
  override fun close()

  sealed class ResizePtyError(msg: String) : Exception(msg) {
    class ProcessExited : ResizePtyError("Process exited")
    class NoPty : ResizePtyError("Process has no PTY")
    data class Errno(val errno: Int, override val message: String) : ResizePtyError("[$errno] $message")
  }
}