// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel

import com.intellij.platform.eel.channels.EelReceiveChannel
import com.intellij.platform.eel.channels.EelSendChannel
import kotlinx.coroutines.Deferred
import org.jetbrains.annotations.ApiStatus

/**
 * Represents some process that was launched via [EelExecApi.spawnProcess].
 *
 */
@ApiStatus.Experimental
sealed interface EelProcess {
  @get:ApiStatus.Experimental
  val pid: EelApi.Pid

  /**
   * Although data transmission via this channel could potentially stall due to overflow of [kotlinx.coroutines.channels.Channel],
   * this method does not allow ensuring that a data chunk was actually delivered to the remote process.
   *
   * Note that each chunk of data is individually and immediately flushed into the process without any intermediate buffer storage.
   */
  @get:ApiStatus.Experimental
  val stdin: EelSendChannel

  @get:ApiStatus.Experimental
  val stdout: EelReceiveChannel

  @get:ApiStatus.Experimental
  val stderr: EelReceiveChannel

  @get:ApiStatus.Experimental
  val exitCode: Deferred<Int>

  /**
   * Sends `SIGKILL` on Unix.
   *
   * Calls [`TerminateProcess`](https://learn.microsoft.com/en-us/windows/win32/api/processthreadsapi/nf-processthreadsapi-terminateprocess)
   * on Windows.
   */
  @ApiStatus.Experimental
  suspend fun kill()

  /**
   * Sends `SIGINT` on Unix.
   * Sends `CTRL+C` on Windows by attaching a console.
   *
   * Warning: This signal can be ignored.
   */
  @ApiStatus.Experimental
  suspend fun interrupt()

  /**
   * Converts to the JVM [Process] which can be used instead of [EelProcess] for compatibility reasons.
   * Note: After conversion, this [EelProcess] shouldn't be used: Use result [Process] instead
   * If the process was launched with PTY, `com.pty4j.PtyProcess` instance is returned.
   */
  @ApiStatus.Experimental
  fun convertToJavaProcess(): Process

  @Throws(ResizePtyError::class)  // Can't use @CheckReturnValue: KTIJ-7061
  @ApiStatus.Experimental
  suspend fun resizePty(columns: Int, rows: Int)

  @ApiStatus.Experimental
  sealed class ResizePtyError(msg: String) : Exception(msg) {
    class ProcessExited : ResizePtyError("Process exited")
    class NoPty : ResizePtyError("Process has no PTY")
    data class Errno(val errno: Int, override val message: String) : ResizePtyError("[$errno] $message")
  }
}

@ApiStatus.Experimental
interface EelPosixProcess : EelProcess {
  /**
   * Sends `SIGTERM` on Unix.
   */
  @ApiStatus.Experimental
  suspend fun terminate()
}

@ApiStatus.Experimental
interface EelWindowsProcess : EelProcess {
  // Nothing yet.
}