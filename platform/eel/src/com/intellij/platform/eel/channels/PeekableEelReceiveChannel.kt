// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.channels

import com.intellij.platform.eel.ReadResult
import com.intellij.platform.eel.ReadResult.EOF
import com.intellij.platform.eel.ReadResult.NOT_EOF
import com.intellij.platform.eel.ThrowsChecked
import org.jetbrains.annotations.ApiStatus
import java.nio.ByteBuffer
import java.nio.charset.Charset

/**
 * An [EelReceiveChannel] wrapper that allows pushing data back so that it can be read again.
 *
 * Data passed to [prepend] is stored in an internal queue and returned by subsequent [receive] calls
 * before any data is taken from the underlying [delegate]. This makes it possible to "peek" into the
 * stream: read some bytes, inspect them, and put the unconsumed remainder back.
 */
@ApiStatus.Experimental
class PeekableEelReceiveChannel(private val delegate: EelReceiveChannel) : EelReceiveChannel {
  private val dataQueue = ArrayDeque<ByteBuffer>()

  /**
   * Pushes [data] back to the front of the channel so that it will be returned by the following [receive] calls
   * before any new data is read from the underlying channel.
   *
   * The buffers are prepended in the given order, i.e. the first argument will be read first. Empty buffers
   * (without remaining bytes) are ignored. The buffers are stored by reference and read starting from their
   * current position, so they should not be modified afterwards.
   */
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

/**
 * Returns a [PeekableEelReceiveChannel] backed by this channel.
 *
 * If the channel is already a [PeekableEelReceiveChannel], it is returned as is; otherwise a new wrapper is created.
 */
@ApiStatus.Experimental
fun EelReceiveChannel.peekable(): PeekableEelReceiveChannel =
  this as? PeekableEelReceiveChannel ?: PeekableEelReceiveChannel(this)

/**
 * Reads data from the channel and passes it to [dataConsumer] until [untilByte] is encountered or the end of the
 * stream is reached.
 *
 * The consumed data is delivered in chunks. The [last] flag passed to [dataConsumer] is `true` for the final chunk
 * that ends right before [untilByte], and `false` for the intermediate chunks. The [untilByte] itself is not passed to
 * the consumer, and any data following it is prepended back to the channel so that it can be read again.
 *
 * Example:
 * ```kotlin
 * coroutineScope {
 *   val pipe = EelPipe(prefersDirectBuffers = false)
 *   launch {
 *     pipe.sink.send(ByteBuffer.wrap(byteArrayOf(1, 2)))
 *     delay(100.milliseconds)
 *     pipe.sink.send(ByteBuffer.wrap(byteArrayOf(3, 4)))
 *     pipe.sink.send(ByteBuffer.wrap(byteArrayOf(5, 6, 7)))
 *   }
 *
 *   val channel = pipe.source.peekable()
 *   channel.readUntil(6.toByte()) { buffer, last ->
 *     val data = ByteArray(buffer.remaining()) { buffer.get(it) }.joinToString { it.toUByte().toString() }
 *     println("$data $last")
 *     // 1, 2 false
 *     // 3, 4 false
 *     // 5 true
 *   }
 * }
 * ```
 *
 * @return `true` if [untilByte] was found, `false` if the end of the stream was reached before that.
 */
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
            dataConsumer(buffer.slice().run { limit(limit() - 1) }, true)
            break@mainLoop
          }
        }

        buffer.flip()
        dataConsumer(buffer.slice(), false)
      }
    }
  }

  return true
}

/**
 * Reads a single line from the channel and decodes it using [charset].
 *
 * The line is read up to and including the next `\n`; a trailing `\r` (i.e. a `\r\n` sequence) is stripped. The line
 * terminator is not included in the result, and the data following it remains available for subsequent reads.
 *
 * @return the decoded line, or `null` if the end of the stream was reached and no data was read.
 */
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