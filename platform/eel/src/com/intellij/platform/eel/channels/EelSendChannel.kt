// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.channels

import com.intellij.platform.eel.ReadResult
import com.intellij.platform.eel.ThrowsChecked
import org.jetbrains.annotations.ApiStatus
import java.nio.ByteBuffer

/**
 * Consumes bytes as buffers. Each [send] writes `0` or more bytes or throws an exception in case of IO error
 */
@ApiStatus.Experimental
interface EelSendChannel {
  /**
   * Writes [src], suspends until written.
   *
   * Most implementations write the whole [src], but some might write partially.
   * In this case, check [src]'s [ByteBuffer.hasRemaining].
   *
   * It is recommended to use [sendWholeBuffer].
   *
   * This method is *not* thread-safe (i.e. you can't send two buffers and the same time).
   *
   * Throws an exception in case of IO error.
   */
  @EelSendApi
  @ApiStatus.Internal
  @Throws(EelSendChannelException::class)
  @ThrowsChecked(EelSendChannelException::class)
  suspend fun send(src: ByteBuffer)

  /**
   * Closes channel for sending. You can't send anything to a closed channel.
   * Receive side will get [com.intellij.platform.eel.ReadResult.EOF]
   *
   * If [err] null, the channel is closed as a regular end of stream,
   * and a call of the corresponding [EelReceiveChannel.receive] will return [ReadResult.EOF].
   *
   * If [err] is not null, the corresponding [EelReceiveChannel.receive] will throw
   * the error wrapped as the cause into [EelReceiveChannelException].
   */
  @ApiStatus.Experimental
  suspend fun close(err: Throwable?)

  /**
   * Channel is closed, and any [send] is guaranteed to return an error.
   * This field is set some time after the channel is closed, so you might encounter an error with [send] even though this field is `false`.
   * Both [close] and [EelReceiveChannel.closeForReceive] set this flag on.
   * Useful only for the case of skipping some unnecessary computations.
   */
  @get:ApiStatus.Experimental
  val isClosed: Boolean

  /**
   * Returns true if the channel implementation works faster with [java.nio.DirectByteBuffer].
   *
   * See also [com.intellij.platform.eel.EelLowLevelObjectsPool.directByteBuffers].
   */
  val prefersDirectBuffers: Boolean
}

/**
 * As [EelSendChannel.send] but writes the whole buffer (coroutine is suspended until buffer gets written).
 * In most cases, you need this function.
 * This method is *not* thread-safe (i.e. you can't send two buffers and the same time).
 */
@OptIn(EelSendApi::class)
@ApiStatus.Experimental
@Throws(EelSendChannelException::class)
@ThrowsChecked(EelSendChannelException::class)
suspend fun EelSendChannel.sendWholeBuffer(src: ByteBuffer) {
  if (this is EelSendChannelCustomSendWholeBuffer) {
    return sendWholeBufferCustom(src)
  }
  do {
    send(src)
  }
  while (src.hasRemaining())
}
