// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.channels

import com.intellij.platform.eel.ReadResult
import com.intellij.platform.eel.ReadResult.EOF
import com.intellij.platform.eel.ReadResult.NOT_EOF
import com.intellij.platform.eel.ThrowsChecked
import org.jetbrains.annotations.ApiStatus
import java.io.ByteArrayOutputStream
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
suspend fun PeekableEelReceiveChannel.readUntil(
  untilByte: Byte,
  dataConsumer: suspend (ByteBuffer, last: Boolean) -> Unit,
): Boolean {
  val bufferSize = 4096
  return readUntil(untilByte, bufferSize, dataConsumer)
}

/**
 * Please read the documentation for the other overload of [readUntil].
 *
 * And please vote for:
 * * KT-86011 KDoc: No tag for inlining documentation from another declaration
 * * KT-15984 Kdoc doesn't support specifying a particular overloaded function or variable in a link
 */
@ThrowsChecked(EelReceiveChannelException::class)
@ApiStatus.Experimental
suspend fun PeekableEelReceiveChannel.readUntil(
  untilByte: Byte,
  bufferSize: Int,
  dataConsumer: suspend (ByteBuffer, Boolean) -> Unit,
): Boolean {
  val buffer = ByteBuffer.allocate(bufferSize)

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
            dataConsumer(buffer.slice().apply { limit(limit() - 1) }, true)
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
 * That pushback is what this is for -- reading a handshake or a header and leaving the rest of the stream to
 * someone else. To read a channel to its end, use [com.intellij.platform.eel.provider.utils.lines], which reads
 * ahead, but is faster.
 *
 * @return the decoded line, or `null` if the end of the stream was reached and no data was read.
 */
@ThrowsChecked(EelReceiveChannelException::class)
@ApiStatus.Experimental
suspend fun PeekableEelReceiveChannel.readLine(charset: Charset): String? {
  return readLine(charset, 4096)
}

/**
 * Please read the documentation for the other overload of [readLine].
 *
 * And please vote for:
 * * KT-86011 KDoc: No tag for inlining documentation from another declaration
 * * KT-15984 Kdoc doesn't support specifying a particular overloaded function or variable in a link
 */
@ThrowsChecked(EelReceiveChannelException::class)
@ApiStatus.Experimental
suspend fun PeekableEelReceiveChannel.readLine(
  charset: Charset,
  bufferSize: Int,
): String? {
  val line = ByteArrayOutputStream()
  val newlineReached = readUntil('\n'.code.toByte(), bufferSize) { buffer, last ->
    if (last && buffer.hasRemaining() && buffer.get(buffer.limit() - 1) == '\r'.code.toByte()) {
      buffer.limit(buffer.limit() - 1)
    }
    line.write(buffer.array(), buffer.position(), buffer.remaining())
  }
  @Suppress("BlockingMethodInNonBlockingContext")  // False positive.
  return if (newlineReached || line.size() != 0) line.toString(charset.name()) else null
}