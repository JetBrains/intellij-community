// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.provider.utils

import com.intellij.platform.eel.ReadResult
import com.intellij.platform.eel.channels.EelReceiveChannel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import org.jetbrains.annotations.ApiStatus
import java.nio.ByteBuffer

@ApiStatus.Internal
class EelOutputChannelImpl(override val prefersDirectBuffers: Boolean) : EelOutputChannel, EelReceiveChannel {
  private var state: MutableStateFlow<State> = MutableStateFlow(State.default())

  /**
   * [buffer] position can ba changed only when [copyInProgress] is true.
   */
  private data class State(
    val closed: Boolean,
    val closedWithError: Throwable?,
    val closedForReceive: Boolean,
    val copyInProgress: Boolean,
    val receiveInProgressGuard: Boolean,
    val parallelReadDetected: Boolean,
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
      fun default(): State = State(
        closed = false,
        closedForReceive = false,
        copyInProgress = false,
        receiveInProgressGuard = false,
        parallelReadDetected = false,
        buffer = ByteBuffer.allocate(0),
        closedWithError = null
      )
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

  override fun ensureClosed(lazyError: () -> Throwable?) {
    state.getAndUpdate {
      if (!it.closed) {
        it.copy(closed = true, closedWithError = lazyError())
      }
      else {
        it
      }
    }.closed
  }

  override suspend fun closeForReceive() {
    state.update { it.copy(closedForReceive = true) }
  }

  @Throws(EelChannelClosedException::class)
  override suspend fun receive(dst: ByteBuffer): ReadResult = receiveImpl(dst) {
    state.waitThenGetAndUpdate(
      condition = { it.buffer.hasRemaining() || it.closed },
      update = { it.copy(copyInProgress = true) }
    )
  }

  @Throws(EelChannelClosedException::class)
  override fun receiveAvailable(dst: ByteBuffer): ReadResult = receiveImpl(dst) {
    state.update { it.copy(copyInProgress = true) }
  }

  private inline fun receiveImpl(dst: ByteBuffer, acquireCopyLock: () -> Unit): ReadResult {
    val state0 = state.updateAndGet {
      if (it.receiveInProgressGuard) it.copy(parallelReadDetected = true) else it.copy(receiveInProgressGuard = true)
    }
    check(!state0.parallelReadDetected) { "concurrent receive is not supported" }
    var error: Throwable? = null
    try {
      acquireCopyLock()
      return doCopy(dst, state.value)
    }
    catch (e: Throwable) {
      error = e
      throw e
    }
    finally {
      val state2 = state.getAndUpdate { it.copy(copyInProgress = false, receiveInProgressGuard = false, parallelReadDetected = false) }
      if (state2.parallelReadDetected) {
        val exception = IllegalStateException("concurrent receive is not supported")
        if (error != null) error.addSuppressed(exception)
        else throw exception
      }
    }
  }

  private fun doCopy(dst: ByteBuffer, stateValue: State): ReadResult {
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
