// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.impl.local

import com.intellij.platform.eel.ReadResult
import com.intellij.platform.eel.channels.EelDelicateApi
import com.intellij.platform.eel.channels.EelReceiveChannel
import com.intellij.platform.eel.provider.utils.consumeAsEelChannel
import java.io.IOException
import java.nio.ByteBuffer

/**
 * A workaround for stdout and stderr from [java.lang.Process].
 *
 * [java.lang.Process.destroy] and [java.lang.Process.destroyForcibly] not only send a signal to the process, but also immediately
 * close all streams despite possibly important data written by the process after receiving the signal.
 *
 * Without this class, an attempt to read anything after calling `destroy` would lead to [IOException] with the message "Stream closed".
 *
 * This class returns an indicator of a closed channel if [isDestroyed] returns `true`.
 * Even though the data is still lost, there's at least no unexpected error.
 *
 * The class does not examine the exception: its message, its type and its wrapping depend on the JDK and on [delegate].
 * Thus, [isDestroyed] must return `true` before the caller calls `destroy`.
 */
internal class StreamClosedAwareEelReceiveChannel(
  private val delegate: EelReceiveChannel,
  private val isDestroyed: () -> Boolean,
) : EelReceiveChannel {
  override val prefersDirectBuffers: Boolean = delegate.prefersDirectBuffers

  override suspend fun receive(dst: ByteBuffer): ReadResult {
    try {
      return delegate.receive(dst)
    }
    catch (error: IOException) {
      if (isDestroyed()) {
        return ReadResult.fromNumberOfReadBytes(-1)
      }
      throw error
    }
  }

  @OptIn(EelDelicateApi::class)
  override fun available(): Int = delegate.available()

  override suspend fun closeForReceive() {
    delegate.closeForReceive()
  }
}

/**
 * [stdout] and [stderr] of [process] that return EOF instead of an error after [destroyForcibly].
 * Call [destroyForcibly] instead of [Process.destroyForcibly]: it marks the process as destroyed before it closes the streams.
 * If the process is not alive, the channels also return EOF: this is a fallback for a `destroy` call from the outside
 * (see [com.intellij.platform.eel.impl.ToJvmConvertableProcess.convertToJVMProcess]).
 */
internal class DestroyAwareOutput(private val process: Process) {
  @Volatile
  private var destroyed = false
  private val isDestroyed = { destroyed || !process.isAlive }

  val stdout: EelReceiveChannel = StreamClosedAwareEelReceiveChannel(process.inputStream.consumeAsEelChannel(), isDestroyed)
  val stderr: EelReceiveChannel = StreamClosedAwareEelReceiveChannel(process.errorStream.consumeAsEelChannel(), isDestroyed)

  fun destroyForcibly() {
    destroyed = true
    process.destroyForcibly()
  }
}
