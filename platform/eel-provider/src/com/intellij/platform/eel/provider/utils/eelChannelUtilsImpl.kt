// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.provider.utils

import com.intellij.platform.eel.ReadResult
import com.intellij.platform.eel.channels.*
import com.intellij.util.io.computeDetached
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.*
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.channels.ReadableByteChannel
import java.nio.channels.SelectionKey
import java.nio.channels.Selector
import java.nio.channels.WritableByteChannel
import java.nio.charset.Charset
import kotlin.coroutines.CoroutineContext

internal class NioReadToEelAdapter(private val readableByteChannel: ReadableByteChannel, private val availableDelegate: () -> Int) : EelReceiveChannel {
  init {
    if (readableByteChannel is java.nio.channels.SocketChannel) {
      readableByteChannel.configureBlocking(false)
    }
  }

  @OptIn(DelicateCoroutinesApi::class)
  override suspend fun receive(dst: ByteBuffer): ReadResult {
    if (!dst.hasRemaining()) return ReadResult.NOT_EOF
    return withContext(Dispatchers.IO) {
      var read = 0
      try {
        if (readableByteChannel is java.nio.channels.SelectableChannel) {
          Selector.open().use { selector ->
            readableByteChannel.register(selector, SelectionKey.OP_READ)
            do {
              while (selector.select(100) == 0) {  // I choose 100 ms at random.
                ensureActive()
              }
              selector.selectedKeys().clear()
              read = readableByteChannel.read(dst)
            }
            while (read == 0)
          }
        }
        else {
          read = computeDetached {
            readableByteChannel.read(dst)
          }
        }
      }
      catch (err: IOException) {
        throw EelReceiveChannelException(this@NioReadToEelAdapter, err)
      }
      ReadResult.fromNumberOfReadBytes(read)
    }
  }

  override fun available(): Int = availableDelegate()

  override suspend fun closeForReceive() {
    withContext(Dispatchers.IO + NonCancellable) {
      readableByteChannel.close()
    }
  }
}

internal class NioWriteToEelAdapter(
  private val writableByteChannel: WritableByteChannel,
  private val flushable: Flushable? = null,
) : EelSendChannel {
  init {
    if (writableByteChannel is java.nio.channels.SelectableChannel) {
      writableByteChannel.configureBlocking(false)
    }
  }

  override fun toString(): String = "NioWriteToEelAdapter[$writableByteChannel]"

  override val isClosed: Boolean get() = !writableByteChannel.isOpen

  @OptIn(DelicateCoroutinesApi::class)
  @EelSendApi
  override suspend fun send(src: ByteBuffer) {
    if (!src.hasRemaining()) return
    withContext(Dispatchers.IO) {
      try {
        if (writableByteChannel is java.nio.channels.SelectableChannel) {
          Selector.open().use { selector ->
            writableByteChannel.register(selector, SelectionKey.OP_WRITE)

            do {
              while (selector.select(100) == 0) {  // I choose 100 ms at random.
                ensureActive()
              }
              selector.selectedKeys().clear()
            }
            while (writableByteChannel.write(src) == 0)
          }
        }
        else {
          computeDetached {
            writableByteChannel.write(src)
          }
        }
        flushable?.flush()
      }
      catch (err: IOException) {
        throw EelSendChannelException(this@NioWriteToEelAdapter, err)
      }
    }
  }

  override suspend fun close(err: Throwable?) {
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
      receiveChannel.closeForReceive()
    }
  }

  // Pipe is a special case we can tell how much bytes are available.
  // In other cases, we do not know.
  // Unblocking read in IJ depends on it, so we can't simply return 0 here not to break unblocking read
  @Suppress("checkedExceptions")
  override fun available(): Int {
    return when (receiveChannel) {
      is EelPipeImpl, is EelOutputChannel -> {
        receiveChannel.available()
      }
      else -> 0
    }
  }

  override fun read(b: ByteArray, off: Int, len: Int): Int = read(ByteBuffer.wrap(b, off, len), len)

  @Throws(IOException::class)
  private fun read(dst: ByteBuffer, len: Int): Int {
    if (!dst.hasRemaining() || len == 0) return 0
    while (true) { // InputStream.read never returns 0 unless closed or dst has size 0
      val r = if (receiveChannel is EelOutputChannel && receiveChannel.available() > 0) {
        receiveChannel.receiveAvailable(dst)
      }
      else {
        runBlocking(blockingContext) {
          receiveChannel.receive(dst)
        }
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
      sendChannel.close(null)
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
      }
      catch (e: IOException) {
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

internal fun ByteBuffer.putPartially(src: ByteBuffer): Int {
  val dst = this
  val bytesBeforeRead = src.remaining()
  // Choose the best approach:
  if (src.remaining() <= dst.remaining()) {
    // Bulk put the whole buffer
    dst.put(src)
  }
  else {
    // Slice, put, and set size back
    val l = src.limit()
    dst.put(src.limit(src.position() + dst.remaining()))
    src.limit(l)
  }
  val bytesRead = bytesBeforeRead - src.remaining()
  return bytesRead
}