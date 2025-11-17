// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.provider.utils

import com.intellij.platform.eel.ReadResult
import com.intellij.platform.eel.channels.*
import com.intellij.platform.eel.provider.utils.EelPipeImpl.State.*
import kotlinx.coroutines.flow.*
import java.io.IOException
import java.nio.ByteBuffer

internal class EelPipeImpl : EelPipe, EelReceiveChannel, EelSendChannelCustomSendWholeBuffer {
  private sealed interface State {
    object Idle : State

    sealed interface TransferState : State {
      val bufferToSend: ByteBuffer
    }

    class ReadyToTransfer(override val bufferToSend: ByteBuffer) : TransferState {
      val initialPos: Int = bufferToSend.position()
    }

    class TransferringNow(override val bufferToSend: ByteBuffer) : TransferState

    class LastTransfer(val currentState: TransferState, val nextState: Closed) : State

    class Closed(val error: Throwable?) : State
  }

  private val state = MutableStateFlow<State>(Idle)

  override val sink: EelSendChannel = this
  override val source: EelReceiveChannel = this

  override suspend fun closePipe(error: Throwable?) {
    sink.close(error)
    source.closeForReceive()
  }

  private val closedToken = ReadyToTransfer(ByteBuffer.allocate(0))

  @EelDelicateApi
  override fun available(): Int =
    availableImpl(state.value)

  private tailrec fun availableImpl(value: State): Int =
    when (value) {
      is Closed, Idle, is TransferringNow -> 0
      is LastTransfer -> availableImpl(value.currentState)
      is ReadyToTransfer -> value.bufferToSend.remaining()
    }

  override suspend fun receive(dst: ByteBuffer): ReadResult {
    var readyToTransfer: ReadyToTransfer
    var transferringNow: TransferringNow
    do {
      readyToTransfer = state
        .mapNotNull { state ->
          when (state) {
            is Closed -> {
              if (state.error != null) {
                state.throwError()
              }
              else {
                closedToken
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
        }
        .first()

      if (readyToTransfer === closedToken) {
        return ReadResult.EOF
      }
      transferringNow = TransferringNow(readyToTransfer.bufferToSend)
    }
    while (!state.compareAndSet(readyToTransfer, transferringNow))

    val newState = run {
      val src = readyToTransfer.bufferToSend
      dst.putPartially(src)
      if (src.hasRemaining())
        ReadyToTransfer(src)
      else
        Idle
    }

    state.update { state ->
      when (state) {
        is TransferringNow -> {
          return@update newState
        }
        is LastTransfer -> {
          if (state.currentState === transferringNow) {
            return@update if (newState == Idle)
              state.nextState
            else
              LastTransfer(newState as TransferState, state.nextState)
          }
        }

        is Closed, Idle, is ReadyToTransfer -> Unit
      }
      error("Bug. Unexpected concurrent modification of state: $transferringNow => ($state | $newState)")
    }

    // It's enough info now to check if the end of the stream is reached,
    // but the previous implementation wouldn't return EOF in this situation.
    return ReadResult.NOT_EOF
  }

  override suspend fun closeForReceive() {
    state.update {
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
    startSending(src).takeWhile { it?.bufferToSend === src }.collect()
  }

  @EelSendApi
  override suspend fun send(src: ByteBuffer) {
    val initialPos = src.position()
    startSending(src)
      .takeWhile { transfer ->
        when (transfer) {
          null -> false
          is TransferringNow -> transfer.bufferToSend === src
          is ReadyToTransfer -> transfer.bufferToSend === src || transfer.initialPos == initialPos
        }
      }
      .collect()
  }

  private suspend fun startSending(src: ByteBuffer): Flow<TransferState?> {
    do {
      val actualState = state
        .mapNotNull {
          when (it) {
            is Closed, Idle -> it
            is LastTransfer -> it.nextState
            is ReadyToTransfer, is TransferringNow -> null
          }
        }
        .first()

      if (actualState is Closed) {
        actualState.throwError()
      }
    }
    while (!state.compareAndSet(actualState, ReadyToTransfer(src)))

    return state.map {
      when (it) {
        is Closed -> throw IOException("Channel is closed")
        is Idle -> null
        is LastTransfer -> it.currentState
        is TransferState -> it
      }
    }
  }

  private fun Closed.throwError(): Nothing {
    // It's important to always wrap `Closed.error` into another exception. Otherwise, the stacktrace of the caller wouldn't be printed.
    throw EelSendChannelException(sink, error?.message?.let { "Pipe was broken with message: $it" } ?: "Channel is closed", error)
  }

  override suspend fun close(err: Throwable?) {
    state.update { oldState ->
      when (oldState) {
        Idle -> Closed(err)
        is TransferState -> LastTransfer(oldState, Closed(err))
        is Closed, is LastTransfer -> oldState
      }
    }
  }

  override val isClosed: Boolean
    get() = when (state.value) {
      is Closed, is LastTransfer -> true
      Idle, is TransferState -> false
    }
}