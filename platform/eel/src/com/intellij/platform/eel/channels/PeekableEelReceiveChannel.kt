// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.channels

import com.intellij.platform.eel.ReadResult
import com.intellij.platform.eel.ReadResult.EOF
import com.intellij.platform.eel.ReadResult.NOT_EOF
import com.intellij.platform.eel.ThrowsChecked
import org.jetbrains.annotations.ApiStatus
import java.nio.ByteBuffer
import java.nio.charset.Charset

@ApiStatus.Experimental
class PeekableEelReceiveChannel(private val delegate: EelReceiveChannel) : EelReceiveChannel {
  private val dataQueue = ArrayDeque<ByteBuffer>()

  fun prepend(vararg data: ByteBuffer) {
    data.reverse()
    for (d in data) {
      if (d.hasRemaining()) {
        dataQueue.addFirst(d)
      }
    }
  }

  @ThrowsChecked(EelReceiveChannelException::class)
  override suspend fun receive(dst: ByteBuffer): ReadResult {
    if (dataQueue.isNotEmpty()) {
      val oldDstPosition = dst.position()
      do {
        val head = dataQueue.removeFirstOrNull() ?: break
        val oldHeadLimit = head.limit()
        head.limit(oldHeadLimit.coerceAtMost(dst.remaining()))
        dst.put(head)
        head.limit(oldHeadLimit)
        if (head.hasRemaining()) {
          dataQueue.addFirst(head)
        }
      }
      while (dst.hasRemaining())

      if (dst.position() != oldDstPosition) {
        return ReadResult.NOT_EOF
      }
    }

    return delegate.receive(dst)
  }

  @ThrowsChecked(EelReceiveChannelException::class)
  @EelDelicateApi
  override fun available(): Int {
    return dataQueue.sumOf { it.remaining() } + delegate.available()
  }

  override suspend fun closeForReceive() {
    dataQueue.clear()
    delegate.closeForReceive()
  }

  override val prefersDirectBuffers: Boolean
    get() = delegate.prefersDirectBuffers
}

@ApiStatus.Experimental
fun EelReceiveChannel.peekable(): PeekableEelReceiveChannel =
  this as? PeekableEelReceiveChannel ?: PeekableEelReceiveChannel(this)

@ThrowsChecked(EelReceiveChannelException::class)
@ApiStatus.Experimental
suspend fun PeekableEelReceiveChannel.readUntil(untilByte: Byte, dataConsumer: suspend (ByteBuffer, last: Boolean) -> Unit): Boolean {
  val buffer = ByteBuffer.allocate(4096)

  mainLoop@ while (true) {
    buffer.clear()
    when (receive(buffer)) {
      EOF -> return false
      NOT_EOF -> {
        buffer.flip()
        while (buffer.hasRemaining()) {
          val b = buffer.get()
          if (b == untilByte) {
            prepend(buffer.slice())
            buffer.flip()
            dataConsumer(buffer.slice().run { limit(limit() - 1) }, false)
            break@mainLoop
          }
        }

        buffer.flip()
        dataConsumer(buffer.slice(), true)
      }
    }
  }

  return true
}

@ThrowsChecked(EelReceiveChannelException::class)
@ApiStatus.Experimental
suspend fun PeekableEelReceiveChannel.readLine(charset: Charset): String? {
  val line = StringBuilder()
  val newlineReached = readUntil('\n'.code.toByte()) { buffer, last ->
    if (last && buffer.hasRemaining() && buffer.get(buffer.limit() - 1) == '\r'.code.toByte()) {
      buffer.limit(buffer.limit() - 1)
    }
    line.append(charset.decode(buffer))
  }
  return if (newlineReached || line.isNotEmpty()) line.toString() else null
}