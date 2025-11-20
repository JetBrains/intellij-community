// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.channels

import com.intellij.platform.eel.ReadResult
import com.intellij.platform.eel.ThrowsChecked
import org.jetbrains.annotations.ApiStatus
import java.nio.ByteBuffer

/**
 * Channel to receive data from
 */
@ApiStatus.Experimental
interface EelReceiveChannel {
  /**
   * Reads data to [dst] but might read less (see buffer position).
   * To read to the end, read until result is [ReadResult.EOF].
   *
   * @return [ReadResult] (see its doc for usage instructions)
   * @throws EelReceiveChannelException if some I/O error occured that certainly can be treated just as [ReadResult.EOF].
   *   The channel is unusable after receiving the error. No methods except [closeForReceive] may be called.
   */
  @Throws(EelReceiveChannelException::class)
  @ThrowsChecked(EelReceiveChannelException::class)
  suspend fun receive(dst: ByteBuffer): ReadResult

  // TODO Think about adding a method `receiveDirect` that accepts only `DirectByteBuffer`.

  /**
   * Behaves like [java.io.InputStream.available]. Especially, it may return false-negative results.
   * I.e., it is possible that [available] returns 0 even though it's possible to read something immediately.
   * Moreover, implementations may return 0 every time.
   *
   * @throws EelReceiveChannelException if some I/O error occured that certainly can be treated just as [ReadResult.EOF].
   *   The channel is unusable after receiving the error. No methods except [closeForReceive] may be called.
   */
  @Throws(EelReceiveChannelException::class)
  @ThrowsChecked(EelReceiveChannelException::class)
  @EelDelicateApi
  fun available(): Int

  /**
   * Closes channel for receiving. You can't receive from the closed channel.
   * Another side will get an error trying to write to this channel.
   *
   * The method is not obligatory to be called. It's useful only to indicate the sender about the end of receiving,
   * but if the sender has already closed the channel on its side, nothing will break down if this method is not called.
   *
   * The method is idempotent. Nothing happens if [closeForReceive] is called for an already closed or a broken channel.
   */
  suspend fun closeForReceive()

  /**
   * Returns true if the channel implementation works faster with [java.nio.DirectByteBuffer].
   *
   * See also [com.intellij.platform.eel.EelLowLevelObjectsPool.directByteBuffers].
   */
  val prefersDirectBuffers: Boolean
}
