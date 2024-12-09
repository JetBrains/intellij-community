// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel

import com.intellij.platform.eel.channels.EelReceiveChannel
import com.intellij.platform.eel.channels.EelSendChannel
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel

/**
 * Represents some process that was launched via [EelExecApi.executeProcess].
 *
 */
interface EelProcess: KillableProcess {
  val pid: EelApiBase.Pid

  /**
   * Although data transmission via this channel could potentially stall due to overflow of [kotlinx.coroutines.channels.Channel],
   * this method does not allow ensuring that a data chunk was actually delivered to the remote process.
   *
   * Note that each chunk of data is individually and immediately flushed into the process without any intermediate buffer storage.
   */
  val stdin: EelSendChannel<ErrorString>
  val stdout: EelReceiveChannel<ErrorString>
  val stderr: EelReceiveChannel<ErrorString>
  val exitCode: Deferred<Int>


  /**
   * Converts to the JVM [Process] which can be used instead of [EelProcess] for compatibility reasons.
   * Note: After conversion, this [EelProcess] shouldn't be used: Use result [Process] instead
   */
  fun convertToJavaProcess(): Process

  @Throws(ResizePtyError::class)  // Can't use @CheckReturnValue: KTIJ-7061
  suspend fun resizePty(columns: Int, rows: Int)

  sealed class ResizePtyError(msg: String) : Exception(msg) {
    class ProcessExited : ResizePtyError("Process exited")
    class NoPty : ResizePtyError("Process has no PTY")
    data class Errno(val errno: Int, override val message: String) : ResizePtyError("[$errno] $message")
  }
}
