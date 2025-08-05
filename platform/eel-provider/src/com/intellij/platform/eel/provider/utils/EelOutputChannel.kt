// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.provider.utils

import com.intellij.platform.eel.ReadResult
import com.intellij.platform.eel.channels.EelReceiveChannel
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combineTransform
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
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
  fun ensureClosed(error: Throwable? = null)
}

@ApiStatus.Internal
class EelOutputChannelImpl : EelOutputChannel, EelReceiveChannel {
  private var state: MutableStateFlow<State> = MutableStateFlow(State.default())

  /**
   * [buffer] position can ba changed only when [copyInProgress] is true.
   */
  private data class State(
    val closed: Boolean,
    val closedWithError: Throwable?,
    val closedForReceive: Boolean,
    val copyInProgress: Boolean,
    val buffer: ByteBuffer,
  ) {
    val available: Int = buffer.remaining()

    override fun equals(other: Any?): Boolean {
      // it's important all instances to be unique because the buffer has mutable position.
      return this === other
    }

    override fun hashCode(): Int {
      return System.identityHashCode(this)
    }

    companion object {
      fun default(): State = State(closed = false, closedForReceive = false, copyInProgress = false, buffer = ByteBuffer.allocate(0), closedWithError = null)
    }
  }

  private suspend fun MutableStateFlow<State>.waitThenGetAndUpdate(condition: (State) -> Boolean, update: (State) -> State): State {
    var prevState: State
    do {
      this.first { condition(it) }
      prevState = this.getAndUpdate {
        if (condition(it)) {
          update(it)
        }
        else it
      }
      val wasUpdated = condition(prevState)
    } while (!wasUpdated)
    return prevState
  }

  override val exposedSource: EelReceiveChannel
    get() = this

  override val available: Flow<Int> get() = state.filter { !it.copyInProgress }.map { it.buffer.remaining() }.distinctUntilChanged()
  override fun available(): Int = state.value.available

  override suspend fun updateBuffer(update: (ByteBuffer) -> ByteBuffer) {
    val prevState = state.waitThenGetAndUpdate({ !it.copyInProgress }) { prevState ->
      if (!prevState.closed && !prevState.closedForReceive) {
        prevState.copy(buffer = update(prevState.buffer))
      }
      else {
        prevState
      }
    }
    if (prevState.closed) {
      if (prevState.closedWithError != null) {
        throw EelChannelClosedException(prevState.closedWithError)
      }
      throw EelChannelClosedException(IllegalStateException("Channel is closed"))
    }
    if (prevState.closedForReceive) {
      throw EelChannelClosedException(IllegalStateException("Channel is closed by receiver"))
    }
  }

  override fun sendEof() {
    state.update { it.copy(closed = true) }
  }

  override fun ensureClosed(error: Throwable?) {
    val wasClosed = state.getAndUpdate {
      it.copy(closed = true)
    }.closed
    if (!wasClosed && error != null) {
      state.update { it.copy(closedWithError = error) }
    }
  }

  override suspend fun closeForReceive() {
    state.update { it.copy(closedForReceive = true) }
  }

  @Throws(EelChannelClosedException::class)
  override suspend fun receive(dst: ByteBuffer): ReadResult {
    val wasCopyInProgress = state.waitThenGetAndUpdate({ it.buffer.hasRemaining() || it.closed }) { it.copy(copyInProgress = true) }.copyInProgress
    check(!wasCopyInProgress) {
      "concurrent receive is not supported"
    }
    try {
      return receiveInternal(dst, state.value)
    } finally {
      state.update { it.copy(copyInProgress = false) }
    }
  }

  @Throws(EelChannelClosedException::class)
  override fun receiveAvailable(dst: ByteBuffer): ReadResult {
    val wasCopyInProgress = state.getAndUpdate { it.copy(copyInProgress = true) }.copyInProgress
    check(!wasCopyInProgress) {
      "concurrent receive is not supported"
    }
    try {
      return receiveInternal(dst, state.value)
    } finally {
      state.update { it.copy(copyInProgress = false) }
    }
  }

  private fun receiveInternal(dst: ByteBuffer, stateValue: State): ReadResult {
    val bytesRead = dst.putPartially(stateValue.buffer)
    if (bytesRead == 0 && stateValue.closed) {
      if (stateValue.closedWithError != null) {
        throw EelChannelClosedException(stateValue.closedWithError)
      }
      return ReadResult.EOF
    }
    else {
      return ReadResult.NOT_EOF
    }
  }
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
  val finished: Flow<Boolean> = flow { emit(false); end.await(); emit(true) }
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
        ByteBuffer.allocate(oldBuffer.remaining() + byteArray.size).also { newBuffer ->
          newBuffer.put(oldBuffer.slice())
          newBuffer.put(byteArray)
          newBuffer.flip()
        }
      }
    }
  }
  sendEof()
}


@ApiStatus.Internal
fun EelOutputChannel(): EelOutputChannel = EelOutputChannelImpl()