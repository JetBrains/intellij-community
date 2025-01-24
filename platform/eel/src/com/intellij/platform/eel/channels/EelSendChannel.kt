// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.channels

import com.intellij.platform.eel.EelResult
import com.intellij.platform.eel.getOr
import org.jetbrains.annotations.CheckReturnValue
import java.nio.ByteBuffer

/**
 * Consumes bytes as buffers. Each [send] writes `0` or more bytes or returns [ERR] in case if IO error
 */
interface EelSendChannel<out ERR : Any> {
  /**
   * Writes [src], suspends until written.
   *
   * Most implementations write the whole [src], but some might write partially.
   * In this case, check [src]'s [ByteBuffer.hasRemaining].
   *
   * It is recommended to use [sendWholeBuffer].
   *
   * @return Either IO [ERR] string or success if [src] was written.
   */
  @EelSendApi
  @CheckReturnValue
  suspend fun send(src: ByteBuffer): EelResult<Unit, ERR>

  /**
   * Closes channel for sending. You can't send anything to a closed channel.
   * Receive side will get [com.intellij.platform.eel.ReadResult.EOF]
   */
  suspend fun close()
}

/**
 * As [EelSendChannel.send] but writes the whole buffer.
 * In most cases, you need this function.
 */
@CheckReturnValue
suspend fun <ERR : Any> EelSendChannel<ERR>.sendWholeBuffer(src: ByteBuffer): EelResult<Unit, ERR> {
  if (this is EelSendChannelCustomSendWholeBuffer) {
    return sendWholeBufferCustom(src)
  }
  var result: EelResult<Unit, ERR>
  do {
    @Suppress("OPT_IN_USAGE")
    result = send(src).also { it.getOr { return it } }
  }
  while (src.hasRemaining())
  return result
}
