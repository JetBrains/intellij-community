// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.channels

import com.intellij.platform.eel.ReadResult
import org.jetbrains.annotations.CheckReturnValue
import java.nio.ByteBuffer

/**
 * Channel to receive data from
 */
interface EelReceiveChannel {
  /**
   * Reads data to [dst] but might read less (see buffer position).
   * To read to the end, read until result is [ReadResult.EOF].
   *
   * @return [ReadResult] (see its doc for usage instructions)
   */
  suspend fun receive(dst: ByteBuffer): ReadResult

  /**
   * Closes channel for receiving. You can't receive from the closed channel.
   * Another side will get an error trying to write to this channel.
   */
  suspend fun close()
}
