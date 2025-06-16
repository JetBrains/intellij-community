// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.impl.local

import com.intellij.platform.eel.ReadResult
import com.intellij.platform.eel.channels.EelReceiveChannel
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
  private val delegate: EelReceiveChannel,
) : EelReceiveChannel {
  override suspend fun receive(dst: ByteBuffer): ReadResult {
    try {
      return delegate.receive(dst)
    }
    catch (error: IOException) {
      val e = error
      if (
        e.message == "Stream closed" && e.stackTrace.firstOrNull()?.className == "java.io.BufferedInputStream"
      ) {
        return ReadResult.fromNumberOfReadBytes(-1)
      }
      throw e
    }
  }

  override suspend fun closeForReceive() {
    delegate.closeForReceive()
  }
}
