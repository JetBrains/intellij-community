// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.provider.utils

import com.intellij.platform.eel.EelResult
import com.intellij.platform.eel.ErrorString
import com.intellij.platform.eel.ReadResult
import com.intellij.platform.eel.channels.EelReceiveChannel
import com.intellij.platform.eel.channels.EelSendChannel
import com.intellij.platform.eel.provider.ResultErrImpl
import com.intellij.platform.eel.provider.ResultOkImpl
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.channels.ClosedSendChannelException
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.nio.ByteBuffer


internal class EelPipeImpl() : EelPipe, EelSendChannel<ErrorString>, EelReceiveChannel<ErrorString> {
  private companion object {
    val OK_EOF = ResultOkImpl(ReadResult.EOF)
    val OK_NOT_EOF = ResultOkImpl(ReadResult.NOT_EOF)
  }

  private val channel = Channel<ByteBuffer>()
  private val bytesInQueueMutex = Mutex(locked = false)

  /**
   * Number of bytes currently waiting to be received by [source].
   * This can be used as a hint for [java.io.InputStream.available]
   */
  @Volatile
  var bytesInQueue: Int = 0
    private set

  override val sink: EelSendChannel<ErrorString> = this
  override val source: EelReceiveChannel<ErrorString> = this

  override suspend fun send(src: ByteBuffer): EelResult<Unit, ErrorString> {
    try {
      bytesInQueueMutex.withLock {
        bytesInQueue += src.remaining()
      }
      channel.send(src)
      return OK_UNIT
    }
    catch (_: ClosedSendChannelException) {
      clearBytesInQueue()
      return ERR_CHANNEL_CLOSED
    }
    catch (e: PipeBrokenException) {
      clearBytesInQueue()
      return e.asErrorResult()
    }
  }

  override suspend fun receive(dst: ByteBuffer): EelResult<ReadResult, ErrorString> {
    val src = try {
      channel.receive()
    }
    catch (_: ClosedReceiveChannelException) {
      clearBytesInQueue()
      return OK_EOF
    }
    catch (e: PipeBrokenException) {
      clearBytesInQueue()
      return e.asErrorResult()
    }
    val bytesToWrite = src.remaining()
    // Choose the best approach:
    if (src.remaining() <= dst.remaining()) {
      // Bulk put the whole buffer
      dst.put(src)
    }
    else {
      // Slice, put, and set size back
      val l = src.limit()
      dst.put(src.limit(src.position() + dst.remaining()))
      src.limit(l)
    }
    bytesInQueueMutex.withLock {
      bytesInQueue -= bytesToWrite - src.remaining()
    }
    return OK_NOT_EOF
  }

  override suspend fun close() {
    if (bytesInQueueMutex.withLock { bytesInQueue } != 0) {
      // We still have some data to be delivered. Let's wait sometime to give change to read it
      delay(200)
    }
    channel.close()
    clearBytesInQueue()
  }


  override fun closePipe(error: Throwable?) {
    if (bytesInQueue > 0) {
      // We still have some data to be delivered. Let's wait sometime to give change to read it
      Thread.sleep(200)
    }
    if (error != null) {
      channel.close(PipeBrokenException(error))
    }
    else {
      channel.close()
    }
  }

  private suspend fun clearBytesInQueue() {
    bytesInQueueMutex.withLock {
      bytesInQueue = 0
    }
  }
}

private class PipeBrokenException(cause: Throwable) : Exception("Pipe was broken with message: ${cause.message}", cause) {

  fun asErrorResult(): EelResult.Error<ErrorString> = ResultErrImpl(message!!)
}