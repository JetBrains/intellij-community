// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.provider.utils

import com.intellij.platform.eel.ReadResult
import com.intellij.platform.eel.channels.EelReceiveChannel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combineTransform
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import org.jetbrains.annotations.ApiStatus
import java.nio.ByteBuffer

@ApiStatus.Internal
class EelChannelClosedException(cause: Throwable) : RuntimeException(cause)

/**
 * This interface is designed to be used inside eel implementation.
 * Corresponding api interface is [EelReceiveChannel].
 *
 * Features:
 * - non-blocking receive
 * - sender provides the buffer to avoid buffer allocation
 * - sender can close the channel and leave the rest of the content in the buffer without waiting
 */
@ApiStatus.Internal
interface EelOutputChannel {
  val exposedSource: EelReceiveChannel
  val available: Flow<Int>
  fun available(): Int
  fun receiveAvailable(dst: ByteBuffer): ReadResult

  /**
   * The buffer position can be modified concurrently at any time,
   * so [update] should be safe to concurrent modifications.
   * But in that case, returning value of [update] will be ignored and [update] will
   * be call again, so [update] should be pure.
   *
   * See [sendWholeBuffer] and [sendUntilEnd] for more high-level api.
   */
  @Throws(EelChannelClosedException::class)
  suspend fun updateBuffer(update: (ByteBuffer) -> ByteBuffer)

  /**
   * Closes the channel for writing. Any further writing is not expected.
   * Should be called only once.
   */
  fun sendEof()

  /**
   * Closes the channel similarly to sendEof, but if the channel is not already closed, can log an error or propagate it to the receiver.
   */
  fun ensureClosed(lazyError: () -> Throwable? = { null })
}

@ApiStatus.Internal
fun EelOutputChannel.ensureClosed(error: Throwable?) {
  ensureClosed { error }
}

@ApiStatus.Internal
suspend fun EelOutputChannel.sendWholeBuffer(src: ByteBuffer) {
  available.first { it == 0 }
  updateBuffer { src }
  available.first { it == 0 }
}

@ApiStatus.Internal
@Throws(EelChannelClosedException::class)
suspend fun EelOutputChannel.sendUntilEnd(flow: Flow<ByteArray>, end: Deferred<*>) {
  val finished: Flow<Boolean> = flow {
    emit(false)
    try {
      end.await()
    }
    catch (e: CancellationException) {
      currentCoroutineContext().ensureActive()
      throw RuntimeException(e)
    }
    emit(true)
  }
  flow.collect { byteArray ->
    available.combineTransform(finished) { a, finished ->
      if (finished || a == 0) {
        emit(Unit)
      }
    }.first()
    if (!end.isCompleted) {
      updateBuffer { ByteBuffer.wrap(byteArray) }
    } else {
      updateBuffer { oldBuffer ->
        // the oldBuffer position can be changed concurrently (it only decreases, but that doesn't matter),
        // so we shouldn't read it twice
        val slice = oldBuffer.slice()
        ByteBuffer.allocate(slice.remaining() + byteArray.size).also { newBuffer ->
          newBuffer.put(slice)
          newBuffer.put(byteArray)
          newBuffer.flip()
        }
      }
    }
  }
  sendEof()
}

@ApiStatus.Internal
fun EelOutputChannel(prefersDirectBuffers: Boolean): EelOutputChannel =
  EelOutputChannelImpl(prefersDirectBuffers)
