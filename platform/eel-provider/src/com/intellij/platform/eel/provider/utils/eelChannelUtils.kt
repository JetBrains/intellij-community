// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.provider.utils

import com.intellij.openapi.progress.Cancellation.ensureActive
import com.intellij.platform.eel.EelResult
import com.intellij.platform.eel.ReadResult.EOF
import com.intellij.platform.eel.channels.EelReceiveChannel
import com.intellij.platform.eel.channels.EelSendChannel
import com.intellij.platform.eel.provider.ResultErrImpl
import com.intellij.platform.eel.provider.ResultOkImpl
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.CheckReturnValue
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.channels.Channels
import java.nio.channels.ReadableByteChannel
import java.nio.channels.WritableByteChannel
import kotlin.coroutines.CoroutineContext


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

/**
 * Copies from [src] to [dst] till [src]'s end ([com.intellij.platform.eel.ReadResult.EOF]).
 * Result is either OK or [CopyResultError.InError] (when src returned an error) or [CopyResultError.OutError] (when dst did so)
 * Channels aren't closed.
 */
@CheckReturnValue
suspend fun <ERR_IN : Any, ERR_OUT : Any> copy(
  src: EelReceiveChannel<ERR_IN>,
  dst: EelSendChannel<ERR_OUT>,
  bufferSize: Int = DEFAULT_BUFFER_SIZE,
  // CPU-bound, but due to the mokk error can't be used in tests. Used default in prod.
  dispatcher: CoroutineDispatcher = Dispatchers.Default,
): EelResult<Unit, CopyResultError<ERR_IN, ERR_OUT>> =
  withContext(dispatcher) {
    assert(bufferSize > 0)
    val buffer = ByteBuffer.allocate(bufferSize)
    while (true) {
      // read data
      when (val r = src.receive(buffer)) {
        is EelResult.Error -> return@withContext ResultErrImpl(CopyResultError.InError(r.error))
        is EelResult.Ok -> if (r.value == EOF) break
      }
      buffer.flip()
      do {
        // write data
        when (val r = dst.send(buffer)) {
          is EelResult.Error -> return@withContext ResultErrImpl(CopyResultError.OutError(r.error))
          is EelResult.Ok -> Unit
        }
      }
      while (buffer.hasRemaining())
      buffer.clear()
      ensureActive()
    }
    return@withContext OK_UNIT
  }
