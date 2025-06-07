// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.provider.utils

import com.intellij.openapi.progress.Cancellation.ensureActive
import com.intellij.platform.eel.ReadResult.EOF
import com.intellij.platform.eel.channels.EelReceiveChannel
import com.intellij.platform.eel.channels.EelSendChannel
import com.intellij.platform.eel.channels.sendWholeBuffer
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.channels.Channels
import java.nio.channels.ReadableByteChannel
import java.nio.channels.WritableByteChannel
import java.nio.charset.Charset
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds


// In most cases, you are encouraged to work with eel channels directly for the best performance.
// But if you need io stream or nio channel, you might use these conversions or copy functions.

// Functions that convert something to an eel channel shouldn't be called several times on the same input.
// After the first call, only an eel channel should be used.

// copy functions copy data from/to an eel channel


fun ReadableByteChannel.consumeAsEelChannel(): EelReceiveChannel = NioReadToEelAdapter(this)
fun WritableByteChannel.asEelChannel(): EelSendChannel = NioWriteToEelAdapter(this)

// Flushes data after each writing.
fun OutputStream.asEelChannel(): EelSendChannel = NioWriteToEelAdapter(Channels.newChannel(this), this)
fun InputStream.consumeAsEelChannel(): EelReceiveChannel = NioReadToEelAdapter(Channels.newChannel(this))
fun EelReceiveChannel.consumeAsInputStream(blockingContext: CoroutineContext = Dispatchers.IO): InputStream =
  InputStreamAdapterImpl(this, blockingContext)

fun EelSendChannel.asOutputStream(blockingContext: CoroutineContext = Dispatchers.IO): OutputStream =
  OutputStreamAdapterImpl(this, blockingContext)

/**
 * Reads data from [receiveChannel] with buffer as big as [bufferSize] and returns it from channel (until [receiveChannel] is closed)
 * Each buffer is fresh (not reused) but not flipped.
 * Errors are thrown out of the channel (directly or wrapped with [IOException] if not throwable).
 */
fun CoroutineScope.consumeReceiveChannelAsKotlin(receiveChannel: EelReceiveChannel, bufferSize: Int = DEFAULT_BUFFER_SIZE): ReceiveChannel<ByteBuffer> =
  consumeReceiveChannelAsKotlinImpl(receiveChannel, bufferSize)

/**
 * Collect data from channel line-by-line using [charset] to convert bytes to chars.
 * Much like [java.io.BufferedReader], we consider CR or CRLF as a new line chars.
 * This API might be slow (as it reads one byte per time) so you might prefer to [readAllBytes] first, then decode it and split by lines.
 * However, for interactive source you can't read till the end, so you use this api.
 *
 * As soon as channel gets closed -- flow finishes.
 * ```kotlin
 *  suspend fun chat(process: EelProcess) {
 *   process.stdout.lines().collect { line ->
 *     if (line == "Nice to meet you") {
 *       process.stdin.sendWholeText("Nice to meet you too!")
 *     }
 *     if (line == "Who are you?") {
 *       process.stdin.sendWholeText("An eel api!")
 *     }
 *   }
 * }
 * ```
 */
fun EelReceiveChannel.lines(charset: Charset): Flow<String> = linesImpl(charset)
fun EelReceiveChannel.lines(): Flow<String> = lines(Charset.defaultCharset())

fun Socket.consumeAsEelChannel(): EelReceiveChannel = consumeAsEelChannelImpl()
fun Socket.asEelChannel(): EelSendChannel = asEelChannelImpl()

/**
 * Bidirectional [kotlinx.coroutines.channels.Channel.RENDEZVOUS] pipe much like [java.nio.channels.Pipe].
 * Closing [sink] makes [source] return [com.intellij.platform.eel.ReadResult.EOF]
 * Closing [source] makes [sink] return and [IOException]
 * Calling [closePipe] closes both [sink] and [source], you might provide custom error that will be reported on a writing attempt.
 */
interface EelPipe {
  val sink: EelSendChannel
  val source: EelReceiveChannel
  fun closePipe(error: Throwable? = null)
}

fun EelPipe(): EelPipe = EelPipeImpl()


/**
 * Reads all data till the end from a channel.
 * Semantics is the same as [InputStream.readAllBytes]
 */
suspend fun EelReceiveChannel.readAllBytes(): ByteArray = withContext(Dispatchers.IO) {
  // The current implementation is suboptimal and might be rewritten, but the API should be the same
  consumeAsInputStream().readAllBytes()
}

/**
 * Result of [copy]
 */
sealed class CopyError(override val cause: Throwable) : IOException() {
  class InError(override val cause: Throwable) : CopyError(cause)
  class OutError(override val cause: Throwable) : CopyError(cause)
}


enum class OnError {
  /**
   * Pretend to error happened, and try again after some time (simple backoff algorithm is used)
   */
  RETRY,

  /**
   * Return with error
   */
  EXIT
}

/**
 * Copies from [src] to [dst] till [src]'s end ([com.intellij.platform.eel.ReadResult.EOF]).
 * The function completes successfully or throws [CopyError.InError] (when src returned an error) or [CopyError.OutError] (when dst did so)
 * Channels aren't closed.
 * [onWriteError] and [onReadError] might be used to configure read/write error processing
 */
@Throws(CopyError::class)
suspend fun copy(
  src: EelReceiveChannel,
  dst: EelSendChannel,
  bufferSize: Int = DEFAULT_BUFFER_SIZE,
  // CPU-bound, but due to the mokk error can't be used in tests. Used default in prod.
  dispatcher: CoroutineDispatcher = Dispatchers.Default,
  onReadError: suspend (IOException) -> OnError = { OnError.EXIT },
  onWriteError: suspend (IOException) -> OnError = { OnError.EXIT },
): Unit = withContext(dispatcher) {
  assert(bufferSize > 0)
  var sendBackoff = backoff()
  var receiveBackoff = backoff()

  val buffer = ByteBuffer.allocate(bufferSize)
  while (true) {
    buffer.clear()
    // read data
    try {
      val r = src.receive(buffer)
      if (r == EOF) {
        break
      }
      else {
        receiveBackoff = backoff()
      }
    }
    catch (error: IOException) {
      when (onReadError(error)) {
        OnError.RETRY -> {
          delay(receiveBackoff.next())
          continue
        }
        OnError.EXIT -> throw CopyError.InError(error)
      }
    }
    buffer.flip()
    do {
      // write data
      try {
        dst.sendWholeBuffer(buffer)
        sendBackoff = backoff()
      }
      catch (error: IOException) {
        when (onWriteError(error)) {
          OnError.RETRY -> {
            delay(sendBackoff.next())
            continue
          }
          OnError.EXIT -> throw CopyError.OutError(error)
        }
      }
    }
    while (buffer.hasRemaining())
    ensureActive()
  }
}

// Slowly increase timeout
private fun backoff(): Iterator<Duration> = ((200..1000 step 200).asSequence() + generateSequence(1000) { 1000 }).map { it.milliseconds }.iterator()
