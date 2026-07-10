// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.provider.utils

import com.intellij.platform.eel.EelLowLevelObjectsPool
import com.intellij.platform.eel.ReadResult
import com.intellij.platform.eel.channels.EelDelicateApi
import com.intellij.platform.eel.channels.EelReceiveChannel
import com.intellij.platform.eel.channels.EelReceiveChannelException
import com.intellij.platform.eel.channels.EelSendApi
import com.intellij.platform.eel.channels.EelSendChannel
import com.intellij.platform.eel.channels.EelSendChannelException
import com.intellij.platform.eel.channels.sendWholeBuffer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.Flushable
import java.io.IOException
import java.io.InputStream
import java.io.InterruptedIOException
import java.io.OutputStream
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousSocketChannel
import java.nio.channels.DatagramChannel
import java.nio.channels.FileChannel
import java.nio.channels.InterruptibleChannel
import java.nio.channels.Pipe
import java.nio.channels.ReadableByteChannel
import java.nio.channels.SelectableChannel
import java.nio.channels.SelectionKey
import java.nio.channels.Selector
import java.nio.channels.SocketChannel
import java.nio.channels.WritableByteChannel
import java.nio.charset.Charset
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract
import kotlin.coroutines.CoroutineContext

internal class NioReadToEelAdapter(
  private val readableByteChannel: ReadableByteChannel,
  private val dispatcher: CoroutineContext = unlimitedDispatcher,
  private val availableDelegate: () -> Int,
) : EelReceiveChannel {
  private val selector: Selector?

  init {
    selector = selectorForNioChannel(readableByteChannel)
    if (selector != null) {
      readableByteChannel.configureBlocking(false)
      readableByteChannel.register(selector, SelectionKey.OP_READ)
    }
  }

  override suspend fun receive(dst: ByteBuffer): ReadResult {
    if (!dst.hasRemaining()) return ReadResult.NOT_EOF
    return withContext(dispatcher) {
      var read = 0
      try {
        if (selector != null && readableByteChannel is SelectableChannel) {
          do {
            while (selector.select(100) == 0) {  // I choose 100 ms at random.
              ensureActive()
              if (!readableByteChannel.isOpen) {
                throw EelReceiveChannelException(this@NioReadToEelAdapter, "The channel is closed")
              }
            }
            selector.selectedKeys().clear()
            read = readableByteChannel.read(dst)
          }
          while (read == 0)
        }
        else {
          read = computeMaybeDetached(readableByteChannel is InterruptibleChannel, dispatcher) {
            try {
              runInterruptible {
                readableByteChannel.read(dst)
              }
            }
            catch (e: java.nio.channels.ClosedByInterruptException) {
              currentCoroutineContext().ensureActive()
              throw EelReceiveChannelException(this@NioReadToEelAdapter, e)
            }
            catch (e: java.nio.channels.AsynchronousCloseException) {
              currentCoroutineContext().ensureActive()
              throw EelReceiveChannelException(this@NioReadToEelAdapter, e)
            }
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
    withContext(dispatcher + NonCancellable) {
      selector?.let(selectorPool::returnBack)
      // Hello Java!
      if (readableByteChannel is SocketChannel) {
        readableByteChannel.shutdownInput()
      }
      else {
        readableByteChannel.close()
      }
    }
  }

  override val prefersDirectBuffers: Boolean =
    readableByteChannel is FileChannel
    || readableByteChannel is DatagramChannel
    || readableByteChannel is SocketChannel
    || readableByteChannel is AsynchronousSocketChannel
    || readableByteChannel is Pipe.SourceChannel
}

internal class NioWriteToEelAdapter(
  private val writableByteChannel: WritableByteChannel,
  private val dispatcher: CoroutineContext = unlimitedDispatcher,
  private val flushable: Flushable? = null,
) : EelSendChannel {
  private val selector: Selector?

  init {
    selector = selectorForNioChannel(writableByteChannel)
    if (selector != null) {
      writableByteChannel.configureBlocking(false)
      writableByteChannel.register(selector, SelectionKey.OP_WRITE)
    }
  }

  override fun toString(): String = "NioWriteToEelAdapter[$writableByteChannel]"

  override val isClosed: Boolean get() = !writableByteChannel.isOpen

  @EelSendApi
  override suspend fun send(src: ByteBuffer) {
    if (!src.hasRemaining()) return
    withContext(dispatcher) {
      try {
        if (selector != null && writableByteChannel is SelectableChannel) {
          do {
            while (selector.select(100) == 0) {  // I choose 100 ms at random.
              ensureActive()
            }
            selector.selectedKeys().clear()
          }
          while (writableByteChannel.write(src) == 0)
        }
        else {
          computeMaybeDetached(writableByteChannel is InterruptibleChannel, dispatcher) {
            try {
              runInterruptible {
                writableByteChannel.write(src)
              }
            }
            catch (e: java.nio.channels.ClosedByInterruptException) {
              currentCoroutineContext().ensureActive()
              throw EelSendChannelException(this@NioWriteToEelAdapter, e)
            }
            catch (e: java.nio.channels.AsynchronousCloseException) {
              currentCoroutineContext().ensureActive()
              throw EelSendChannelException(this@NioWriteToEelAdapter, e)
            }
          }
        }
        if (flushable != null) {
          computeDetached {
            flushable.flush()
          }
        }
      }
      catch (err: IOException) {
        throw EelSendChannelException(this@NioWriteToEelAdapter, err)
      }
    }
  }

  override suspend fun close(err: Throwable?) {
    withContext(dispatcher + NonCancellable) {
      selector?.let(selectorPool::returnBack)
      try {
        flushable?.flush()
      }
      catch (_: IOException) {
        // IO exception on close might be ignored
      }

      // Hello Java!
      if (writableByteChannel is SocketChannel) {
        writableByteChannel.shutdownOutput()
      }
      else {
        writableByteChannel.close()
      }
    }
  }

  override val prefersDirectBuffers: Boolean =
    writableByteChannel is FileChannel
    || writableByteChannel is DatagramChannel
    || writableByteChannel is SocketChannel
    || writableByteChannel is AsynchronousSocketChannel
    || writableByteChannel is Pipe.SinkChannel
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
      oneByte.flip()
      return oneByte.get().toInt()
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
      is EelPipe, is EelOutputChannel -> {
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
        try {
          runBlocking(blockingContext) {
            receiveChannel.receive(dst)
          }
        } catch (e: InterruptedException) {
          throw InterruptedIOException().apply {
            addSuppressed(e)
          }
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
    oneByte.clear()
    oneByte.put(b.toByte())
    oneByte.flip()
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

internal fun CoroutineScope.consumeReceiveChannelAsKotlinImpl(receiveChannel: EelReceiveChannel): ReceiveChannel<ByteBuffer> {
  val channel = Channel<ByteBuffer>()
  launch {
    @OptIn(EelDelicateApi::class)
    val pool = if (receiveChannel.prefersDirectBuffers) EelLowLevelObjectsPool.directByteBuffers else EelLowLevelObjectsPool.fakeByteBufferPool
    while (true) {
      val buffer = pool.borrow()
      try {
        val r = receiveChannel.receive(buffer)
        when (r) {
          ReadResult.EOF -> {
            channel.close()
            break
          }
          ReadResult.NOT_EOF -> {
            // Direct buffers are likely to get lost from the pool and collected by GC, but it just brings a tiny performance penalty.
            buffer.flip()
            channel.send(buffer)
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
    tmpBuffer.flip()
    val b = tmpBuffer.get().toInt()
    result.write(b)
    if (b == 10) {
      emitBuffer()
      result = ByteArrayOutputStream()
    }
  }
}

private val selectorPool = EelLowLevelObjectsPool<Selector>(
  10, // 10 is chosen at random.
  factory = Selector::open,
  returnValidator = {
    it.keys().forEach(SelectionKey::cancel)
    true
  }
)

@OptIn(ExperimentalContracts::class)
private fun selectorForNioChannel(channel: java.nio.channels.Channel): Selector? {
  contract {
    returnsNotNull() implies (channel is SelectableChannel)
  }
  return if (channel is SelectableChannel) selectorPool.borrow() else null
}

@OptIn(DelicateCoroutinesApi::class)
private suspend fun <T> computeMaybeDetached(undispatched: Boolean, dispatcher: CoroutineContext, action: suspend () -> T): T {
  return if (undispatched) {
    calledDirectly(action)
  }
  else {
    computeDetached(dispatcher) { calledFromComputeDetached(action) }
  }
}

/** This thin wrapper exists only to explain data flow in stacktraces better. */
private suspend fun <T> calledFromComputeDetached(action: suspend () -> T): T {
  return action()
}

/** This thin wrapper exists only to explain data flow in stacktraces better. */
private suspend fun <T> calledDirectly(action: suspend () -> T): T {
  return action()
}
