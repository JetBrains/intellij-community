// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.provider.utils

import com.intellij.openapi.progress.Cancellation.ensureActive
import com.intellij.platform.eel.EelResult
import com.intellij.platform.eel.ReadResult.EOF
import com.intellij.platform.eel.channels.EelReceiveChannel
import com.intellij.platform.eel.channels.EelSendChannel
import com.intellij.platform.eel.channels.sendWholeBuffer
import com.intellij.platform.eel.provider.ResultErrImpl
import com.intellij.platform.eel.provider.ResultOkImpl
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.CheckReturnValue
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


fun ReadableByteChannel.consumeAsEelChannel(): EelReceiveChannel<IOException> = NioReadToEelAdapter(this)
fun WritableByteChannel.asEelChannel(): EelSendChannel<IOException> = NioWriteToEelAdapter(this)

// Flushes data after each writing.
fun OutputStream.asEelChannel(): EelSendChannel<IOException> = NioWriteToEelAdapter(Channels.newChannel(this), this)
fun InputStream.consumeAsEelChannel(): EelReceiveChannel<IOException> = NioReadToEelAdapter(Channels.newChannel(this))
fun EelReceiveChannel<IOException>.consumeAsInputStream(blockingContext: CoroutineContext = Dispatchers.IO): InputStream =
  InputStreamAdapterImpl(this, blockingContext)

fun EelSendChannel<IOException>.asOutputStream(blockingContext: CoroutineContext = Dispatchers.IO): OutputStream =
  OutputStreamAdapterImpl(this, blockingContext)

/**
 * Reads data from [receiveChannel] with buffer as big as [bufferSize] and returns it from channel (until [receiveChannel] is closed)
 * Each buffer is fresh (not reused) but not flipped.
 * Errors are thrown out of the channel (directly or wrapped with [IOException] if not throwable).
 */
fun CoroutineScope.consumeReceiveChannelAsKotlin(receiveChannel: EelReceiveChannel<*>, bufferSize: Int = DEFAULT_BUFFER_SIZE): ReceiveChannel<ByteBuffer> =
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
 *   process.stdout.lines().collect {
 *     val line = it.getOrThrow()
 *     if (line == "Nice to meet you") {
 *       process.stdin.sendWholeText("Nice to meet you too!").getOrThrow()
 *     }
 *     if (line == "Who are you?") {
 *       process.stdin.sendWholeText("An eel api!").getOrThrow()
 *     }
 *   }
 * }
 * ```
 */
fun <E : Any> EelReceiveChannel<E>.lines(charset: Charset): Flow<EelResult<String, E>> = linesImpl(charset)
fun <E : Any> EelReceiveChannel<E>.lines(): Flow<EelResult<String, E>> = lines(Charset.defaultCharset())

fun Socket.consumeAsEelChannel(): EelReceiveChannel<IOException> = consumeAsEelChannelImpl()
fun Socket.asEelChannel(): EelSendChannel<IOException> = asEelChannelImpl()

/**
 * Bidirectional [kotlinx.coroutines.channels.Channel.RENDEZVOUS] pipe much like [java.nio.channels.Pipe].
 * Closing [sink] makes [source] return [com.intellij.platform.eel.ReadResult.EOF]
 * Closing [source] makes [sink] return and [IOException]
 * Calling [closePipe] closes both [sink] and [source], you might provide custom error that will be reported on a writing attempt.
 */
interface EelPipe {
  val sink: EelSendChannel<IOException>
  val source: EelReceiveChannel<IOException>
  fun closePipe(error: Throwable? = null)
}

fun EelPipe(): EelPipe = EelPipeImpl()


/**
 * Reads all data till the end from a channel.
 * Semantics is the same as [InputStream.readAllBytes]
 */
suspend fun EelReceiveChannel<IOException>.readAllBytes(): EelResult<ByteArray, IOException> = withContext(Dispatchers.IO) {
  // The current implementation is suboptimal and might be rewritten, but the API should be the same
  try {
    ResultOkImpl(consumeAsInputStream().readAllBytes())
  }
  catch (e: IOException) {
    ResultErrImpl(e)
  }
}

/**
 * Result of [copy]
 */
sealed interface CopyResultError<ERR_IN : Any, ERR_OUT : Any> {
  data class InError<ERR_IN : Any, ERR_OUT : Any>(val inError: ERR_IN) : CopyResultError<ERR_IN, ERR_OUT>
  data class OutError<ERR_IN : Any, ERR_OUT : Any>(val outError: ERR_OUT) : CopyResultError<ERR_IN, ERR_OUT>
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
 * Result is either OK or [CopyResultError.InError] (when src returned an error) or [CopyResultError.OutError] (when dst did so)
 * Channels aren't closed.
 * [onWriteError] and [onReadError] might be used to configure read/write error processing
 */
@CheckReturnValue
suspend fun <ERR_IN : Any, ERR_OUT : Any> copy(
  src: EelReceiveChannel<ERR_IN>,
  dst: EelSendChannel<ERR_OUT>,
  bufferSize: Int = DEFAULT_BUFFER_SIZE,
  // CPU-bound, but due to the mokk error can't be used in tests. Used default in prod.
  dispatcher: CoroutineDispatcher = Dispatchers.Default,
  onReadError: suspend (ERR_IN) -> OnError = { OnError.EXIT },
  onWriteError: suspend (ERR_OUT) -> OnError = { OnError.EXIT },
): EelResult<Unit, CopyResultError<ERR_IN, ERR_OUT>> =
  withContext(dispatcher) {
    assert(bufferSize > 0)
    var sendBackoff = backoff()
    var receiveBackoff = backoff()
    val buffer = ByteBuffer.allocate(bufferSize)
    while (true) {
      buffer.clear()
      // read data
      when (val r = src.receive(buffer)) {
        is EelResult.Error -> {
          when (onReadError(r.error)) {
            OnError.RETRY -> {
              delay(receiveBackoff.next())
              continue
            }
            OnError.EXIT -> return@withContext ResultErrImpl(CopyResultError.InError(r.error))
          }
        }
        is EelResult.Ok -> if (r.value == EOF) {
          break
        }
        else {
          receiveBackoff = backoff()
        }
      }
      buffer.flip()
      do {
        // write data
        when (val r = dst.sendWholeBuffer(buffer)) {
          is EelResult.Error -> {
            when (onWriteError(r.error)) {
              OnError.RETRY -> {
                delay(sendBackoff.next())
                continue
              }
              OnError.EXIT -> return@withContext ResultErrImpl(CopyResultError.OutError(r.error))
            }

          }
          is EelResult.Ok -> {
            sendBackoff = backoff()
          }
        }
      }
      while (buffer.hasRemaining())
      ensureActive()
    }
    return@withContext OK_UNIT
  }

// Slowly increase timeout
private fun backoff(): Iterator<Duration> = ((200..1000 step 200).asSequence() + generateSequence(1000) { 1000 }).map { it.milliseconds }.iterator()