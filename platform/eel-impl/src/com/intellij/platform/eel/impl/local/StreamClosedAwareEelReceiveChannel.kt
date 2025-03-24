// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.impl.local

import com.intellij.platform.eel.EelResult
import com.intellij.platform.eel.ReadResult
import com.intellij.platform.eel.channels.EelReceiveChannel
import com.intellij.platform.eel.provider.ResultOkImpl
import java.io.IOException
import java.nio.ByteBuffer

/**
 * A workaround for stdout and stderr from [java.lang.Process].
 *
 * [java.lang.Process.destroy] and [java.lang.Process.destroyForcibly] not only send a some signal to the process, but also immediately
 * close all streams despite possibly important data written by the process after receiving the signal.
 *
 * Without this class, an attempt to read anything after calling `destroy` would lead to [IOException] with the message "Stream closed".
 *
 * This class returns an indicator of a closed channel if the process was destroyed.
 * Even though the data is still lost, there's at least no unexpected error.
 */
internal class StreamClosedAwareEelReceiveChannel(
  private val delegate: EelReceiveChannel<IOException>,
) : EelReceiveChannel<IOException> {
  override suspend fun receive(dst: ByteBuffer): EelResult<ReadResult, IOException> =
    when (val result = delegate.receive(dst)) {
      is EelResult.Ok -> result
      is EelResult.Error -> {
        val e = result.error
        if (
          e.javaClass == IOException::class.java &&
          e.message == "Stream closed" &&
          e.stackTrace.firstOrNull()?.className == "java.io.BufferedInputStream"
        ) {
          ResultOkImpl(ReadResult.fromNumberOfReadBytes(-1))
        }
        else result
      }
    }

  override suspend fun close(): Unit = delegate.close()
}