// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.provider.utils

import com.intellij.platform.eel.ReadResult
import com.intellij.platform.eel.channels.*
import com.intellij.platform.eel.provider.utils.EelPipeImpl.State.*
import kotlinx.coroutines.CompletableDeferred
import java.io.IOException
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicReference
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

internal class EelPipeImpl(
  private val debugLabel: String,
  override val prefersDirectBuffers: Boolean,
) : EelPipe, EelReceiveChannel, EelSendChannelCustomSendWholeBuffer {
  private sealed class State {
    override fun toString(): String = javaClass.simpleName

    object Idle : State()

    sealed class TransferState : State() {
      abstract val bufferToSend: ByteBuffer
    }

    class ReadyToTransfer(override val bufferToSend: ByteBuffer) : TransferState() {
      val initialPos: Int = bufferToSend.position()
    }

    class TransferringNow(override val bufferToSend: ByteBuffer) : TransferState()

    class LastTransfer(val currentState: TransferState, val nextState: Closed) : State()

    class Closed(val error: Throwable?) : State()
  }

  override fun toString(): String = "EelPipe($debugLabel)"

  private val state = AtomicReference<Pair<State, CompletableDeferred<Unit>>>(Idle to CompletableDeferred())

  @OptIn(ExperimentalContracts::class)
  private suspend inline fun waitWhile(filter: (State) -> Boolean) {
    contract {
      callsInPlace(filter, InvocationKind.AT_LEAST_ONCE)
    }
    while (true) {
      val (currentState, nextStateDeferred) = state.get()
      val result = filter(currentState)
      if (!result) {
        break
      }
      nextStateDeferred.await()
    }
  }

  @OptIn(ExperimentalContracts::class)
  private suspend inline fun <S : State> maybeUpdate(mapper: (State) -> S?): S {
    contract {
      callsInPlace(mapper, InvocationKind.AT_LEAST_ONCE)
    }
    while (true) {
      val currentPair = state.get()
      val (currentState, nextStateDeferred) = currentPair
      val newState = mapper(currentState)
      when {
        newState == null -> {
          nextStateDeferred.await()
        }
        state.compareAndSet(currentPair, newState to CompletableDeferred()) -> {
          nextStateDeferred.complete(Unit)
          return newState
        }
      }
    }
  }

  override val sink: EelSendChannel = this
  override val source: EelReceiveChannel = this

  override suspend fun closePipe(error: Throwable?) {
    sink.close(error)
    source.closeForReceive()
  }

  @EelDelicateApi
  override fun available(): Int =
    availableImpl(state.get().first)

  private tailrec fun availableImpl(value: State): Int =
    when (value) {
      is Closed, Idle, is TransferringNow -> 0
      is LastTransfer -> availableImpl(value.currentState)
      is ReadyToTransfer -> value.bufferToSend.remaining()
    }

  override suspend fun receive(dst: ByteBuffer): ReadResult {
    val transferringNow: TransferringNow = maybeUpdate { state ->
      val readyToTransfer =
        when (state) {
          is Closed -> {
            if (state.error != null) {
              state.throwError()
            }
            else {
              return ReadResult.EOF
            }
          }
          Idle -> null
          is LastTransfer -> when (val c = state.currentState) {
            is ReadyToTransfer -> c
            is TransferringNow -> null
          }
          is ReadyToTransfer -> state
          is TransferringNow -> null
        }
        ?: return@maybeUpdate null

      TransferringNow(readyToTransfer.bufferToSend)
    }

    val newState = run {
      val src = transferringNow.bufferToSend
      dst.putPartially(src)
      if (src.hasRemaining())
        ReadyToTransfer(src)
      else
        Idle
    }

    maybeUpdate { state ->
      when (state) {
        transferringNow -> {
          return@maybeUpdate newState
        }
        is LastTransfer -> {
          if (state.currentState === transferringNow) {
            return@maybeUpdate if (newState == Idle)
              state.nextState
            else
              LastTransfer(newState as TransferState, state.nextState)
          }
        }

        is TransferState, is Closed, Idle -> Unit
      }
      error("Bug. Unexpected concurrent modification of state: $transferringNow => ($state | $newState)")
    }

    // It's enough info now to check if the end of the stream is reached,
    // but the previous implementation wouldn't return EOF in this situation.
    return ReadResult.NOT_EOF
  }

  override suspend fun closeForReceive() {
    maybeUpdate {
      val outerError = RuntimeException("Closed for receiving")
      when (it) {
        is LastTransfer -> {
          it.nextState.error?.let(outerError::addSuppressed)
          Closed(outerError)
        }
        is Closed -> it
        Idle, is ReadyToTransfer, is TransferringNow -> Closed(outerError)
      }
    }
  }

  override suspend fun sendWholeBufferCustom(src: ByteBuffer) {
    startSending(src)
    waitWhile { it.currentTransfer()?.bufferToSend === src }
  }

  @EelSendApi
  override suspend fun send(src: ByteBuffer) {
    val initialPos = src.position()
    startSending(src)
    waitWhile {
      when (val transfer = it.currentTransfer()) {
        null -> false
        is TransferringNow -> transfer.bufferToSend === src
        is ReadyToTransfer -> transfer.bufferToSend === src || transfer.initialPos == initialPos
      }
    }
  }

  private suspend fun startSending(src: ByteBuffer) {
    maybeUpdate { state ->
      when (state) {
        is Closed -> state.throwError()
        is LastTransfer -> state.nextState.throwError()
        is Idle -> ReadyToTransfer(src)
        is TransferState -> null
      }
    }
  }

  private fun State.currentTransfer(): TransferState? =
    when (this) {
      is Closed -> throw IOException("Channel is closed", error)
      is Idle -> null
      is LastTransfer -> currentState
      is TransferState -> this
    }


  private fun Closed.throwError(): Nothing {
    // It's important to always wrap `Closed.error` into another exception. Otherwise, the stacktrace of the caller wouldn't be printed.
    throw EelSendChannelException(sink, error?.message?.let { "Pipe was broken with message: $it" } ?: "Channel is closed", error)
  }

  override suspend fun close(err: Throwable?) {
    maybeUpdate { oldState ->
      when (oldState) {
        Idle -> Closed(err)
        is TransferState -> LastTransfer(oldState, Closed(err))
        is Closed, is LastTransfer -> oldState
      }
    }
  }

  override val isClosed: Boolean
    get() = when (state.get().first) {
      is Closed, is LastTransfer -> true
      Idle, is TransferState -> false
    }
}