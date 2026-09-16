package com.jetbrains.lsp.implementation

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import kotlinx.io.Buffer
import kotlinx.io.Source
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.channels.Channels
import java.nio.channels.ReadableByteChannel
import kotlin.concurrent.thread

/**
 * Creates a [ByteReader] over a blocking [InputStream].
 *
 * On Windows the reader owns a daemon thread named [readerThreadName].
 * Release that thread after use with [joinReaderThread].
 */
fun inputStreamByteReader(inputStream: InputStream, readerThreadName: String): ByteReader =
  when {
    isWindows -> WindowsInputStreamByteReader(inputStream, readerThreadName)
    else -> InputStreamByteReader(inputStream)
  }

/**
 * Waits up to [timeoutMillis] for the Windows reader thread to exit.
 * A no-op for a reader without a thread.
 * The wait is not cancellable, so a canceled caller still observes the thread.
 */
suspend fun ByteReader.joinReaderThread(timeoutMillis: Long) {
  val reader = this as? WindowsInputStreamByteReader ?: return
  withContext(NonCancellable + Dispatchers.IO) {
    reader.join(timeoutMillis)
  }
}

private val isWindows: Boolean = System.getProperty("os.name", "").startsWith("windows", ignoreCase = true)

private class InputStreamByteReader(inputStream: InputStream) : ByteReader {
  private val channel: ReadableByteChannel = Channels.newChannel(inputStream)
  private val backingBuffer = Buffer()
  private val nioBuffer = ByteBuffer.allocate(DEFAULT_BUFFER_SIZE)

  @Volatile private var _closed = false
  @Volatile private var _closedCause: Throwable? = null

  override val isClosedForRead: Boolean get() = _closed
  override val closedCause: Throwable? get() = _closedCause
  override val readBuffer: Source get() = backingBuffer

  override suspend fun awaitContent(min: Int): Boolean {
    if (_closed) return false
    nioBuffer.clear()
    val count = try {
      runInterruptible(Dispatchers.IO) { channel.read(nioBuffer) }
    }
    catch (_: IOException) {
      _closed = true
      return false
    }
    if (count <= 0) {
      _closed = true
      return false
    }
    nioBuffer.flip()
    backingBuffer.write(nioBuffer.array(), nioBuffer.arrayOffset() + nioBuffer.position(), count)
    return backingBuffer.size >= min
  }

  override fun cancel(cause: Throwable?) {
    _closedCause = cause
    _closed = true
    try {
      channel.close()
    }
    catch (_: Exception) {
    }
  }
}

/**
 * Windows-specific [ByteReader] backed by a daemon thread that owns the blocking pipe read.
 *
 * On Windows [Thread.interrupt] does not interrupt a blocking `ReadFile` on an anonymous pipe.
 * [runInterruptible] therefore cannot be used safely: coroutine cancellation triggers
 * [java.nio.channels.spi.AbstractInterruptibleChannel]'s `postInterrupt` which calls
 * `CloseHandle` from the *interrupting* thread while `ReadFile` is still in progress on the IO
 * thread, deadlocking both. The daemon thread here owns the blocking `ReadFile` and may stay
 * blocked until the pipe's write end is closed (peer process exits), while the coroutine side
 * cancels immediately and cleanly through the [Channel].
 */
private class WindowsInputStreamByteReader(inputStream: InputStream, threadName: String) : ByteReader {
  private val pipe: Channel<ByteArray> = Channel(Channel.UNLIMITED)
  private val backingBuffer = Buffer()

  @Volatile private var _closed = false
  @Volatile private var _closedCause: Throwable? = null

  private val readerThread: Thread = thread(
    start = true,
    isDaemon = true,
    name = threadName
  ) {
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    try {
      while (true) {
        val n = inputStream.read(buffer)
        if (n < 0) break
        if (pipe.trySend(buffer.copyOf(n)).isFailure) break
      }
    }
    catch (_: IOException) {
    }
    finally {
      pipe.close()
    }
  }

  override val isClosedForRead: Boolean get() = _closed
  override val closedCause: Throwable? get() = _closedCause
  override val readBuffer: Source get() = backingBuffer

  override suspend fun awaitContent(min: Int): Boolean {
    if (_closed) return false
    val chunk = pipe.receiveCatching().getOrNull() ?: run {
      _closed = true
      return false
    }
    backingBuffer.write(chunk)
    return backingBuffer.size >= min
  }

  override fun cancel(cause: Throwable?) {
    _closedCause = cause
    _closed = true
    pipe.close()
    // Do NOT close the InputStream: on Windows CloseHandle() on the pipe's read handle while
    // the background thread is inside ReadFile() blocks indefinitely. The thread exits once
    // the pipe's write end is closed (peer process exits).
  }

  fun join(timeoutMillis: Long) {
    try {
      readerThread.join(timeoutMillis)
    }
    catch (_: InterruptedException) {
      Thread.currentThread().interrupt()
    }
  }
}
