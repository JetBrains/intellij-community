// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.provider.utils

import com.intellij.platform.eel.ReadResult
import com.intellij.platform.eel.channels.EelReceiveChannel
import com.intellij.platform.eel.channels.EelSendChannel
import com.intellij.platform.eel.channels.sendWholeBuffer
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.*
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.channels.ReadableByteChannel
import java.nio.channels.WritableByteChannel
import java.nio.charset.Charset
import kotlin.coroutines.CoroutineContext

internal class NioReadToEelAdapter(private val readableByteChannel: ReadableByteChannel) : EelReceiveChannel {
  override suspend fun receive(dst: ByteBuffer): ReadResult = withContext(Dispatchers.IO) {
    val read = readableByteChannel.read(dst)
    ReadResult.fromNumberOfReadBytes(read)
  }

  override suspend fun close() {
    withContext(Dispatchers.IO + NonCancellable) {
      readableByteChannel.close()
    }
  }
}

internal class NioWriteToEelAdapter(
  private val writableByteChannel: WritableByteChannel,
  private val flushable: Flushable? = null,
) : EelSendChannel {

  override val closed: Boolean get() = !writableByteChannel.isOpen

  override suspend fun send(src: ByteBuffer): Unit = withContext(Dispatchers.IO) {
    writableByteChannel.write(src)
    flushable?.flush()
  }

  override suspend fun close() {
    withContext(Dispatchers.IO + NonCancellable) {
      try {
        flushable?.flush()
      }
      catch (_: IOException) {
        // IO exception on close might be ignored
      }
      writableByteChannel.close()
    }
  }
}

internal class InputStreamAdapterImpl(
  private val receiveChannel: EelReceiveChannel,
  private val blockingContext: CoroutineContext,

  ) : InputStream() {
  private val oneByte = ByteBuffer.allocate(1)
  override fun read(): Int {
    oneByte.clear()
    if (read(oneByte, 1) == -1) {
      return -1
    }
    else {
      return oneByte.flip().get().toInt()
    }
  }

  override fun close() {
    runBlocking(blockingContext) {
      receiveChannel.close()
    }
  }

  // Pipe is a special case we can tell how much bytes are available.
  // In other cases, we do not know.
  // Unblocking read in IJ depends on it, so we can't simply return 0 here not to break unblocking read
  override fun available(): Int = (receiveChannel as? EelPipeImpl)?.bytesInQueue ?: 0

  override fun read(b: ByteArray, off: Int, len: Int): Int = read(ByteBuffer.wrap(b, off, len), len)

  @Throws(IOException::class)
  private fun read(dst: ByteBuffer, len: Int): Int {
    if (!dst.hasRemaining() || len == 0) return 0
    while (true) { // InputStream.read never returns 0 unless closed or dst has size 0
      val r = runBlocking(blockingContext) {
        receiveChannel.receive(dst)
      }
      when (r) {
        ReadResult.EOF -> {
          return -1
        }
        ReadResult.NOT_EOF -> {
          val bytesRead = len - dst.remaining()
          if (bytesRead > 0) { // See comment on while(true)
            return bytesRead
          }
        }
      }
    }
  }
}

internal class OutputStreamAdapterImpl(
  private val sendChannel: EelSendChannel,
  private val blockingContext: CoroutineContext,
) : OutputStream() {
  private val oneByte = ByteBuffer.allocate(1)
  override fun write(b: Int) {
    oneByte.clear().put(b.toByte()).flip()
    write(oneByte)
  }

  override fun write(b: ByteArray, off: Int, len: Int) {
    write(ByteBuffer.wrap(b, off, len))
  }

  override fun flush() = Unit

  @Throws(IOException::class)
  private fun write(buffer: ByteBuffer) {
    runBlocking(blockingContext) {
      sendChannel.sendWholeBuffer(buffer)
    }
  }

  override fun close() {
    runBlocking(blockingContext) {
      sendChannel.close()
    }
  }
}

internal fun CoroutineScope.consumeReceiveChannelAsKotlinImpl(receiveChannel: EelReceiveChannel, bufferSize: Int): ReceiveChannel<ByteBuffer> {
  val channel = Channel<ByteBuffer>()
  launch {
    while (true) {
      val buffer = ByteBuffer.allocate(bufferSize)
      try {
        val r = receiveChannel.receive(buffer)
        when (r) {
          ReadResult.EOF -> {
            channel.close()
            break
          }
          ReadResult.NOT_EOF -> {
            channel.send(buffer.flip())
          }
        }
      } catch (e: IOException) {
        channel.close(e)
        break
      }
    }
  }
  return channel
}

internal fun Socket.consumeAsEelChannelImpl(): EelReceiveChannel =
  channel?.consumeAsEelChannel() ?: inputStream.consumeAsEelChannel()

internal fun Socket.asEelChannelImpl(): EelSendChannel =
  channel?.asEelChannel() ?: outputStream.asEelChannel()

internal fun EelReceiveChannel.linesImpl(charset: Charset): Flow<String> = flow {
  val tmpBuffer = ByteBuffer.allocate(1)
  var result = ByteArrayOutputStream()
  while (true) {
    tmpBuffer.rewind()
    suspend fun emitBuffer() {
      emit(charset.decode(ByteBuffer.wrap(result.toByteArray())).toString())
    }

    val r = receive(tmpBuffer)

    if (r == ReadResult.EOF) {
      emitBuffer()
      return@flow
    }
    val b = tmpBuffer.flip().get().toInt()
    result.write(b)
    if (b == 10) {
      emitBuffer()
      result = ByteArrayOutputStream()
    }
  }
}