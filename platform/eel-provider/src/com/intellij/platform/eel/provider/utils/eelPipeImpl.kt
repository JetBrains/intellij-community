// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.provider.utils

import com.intellij.platform.eel.EelResult
import com.intellij.platform.eel.ErrorString
import com.intellij.platform.eel.ReadResult
import com.intellij.platform.eel.channels.EelReceiveChannel
import com.intellij.platform.eel.channels.EelSendChannel
import com.intellij.platform.eel.provider.ResultErrImpl
import com.intellij.platform.eel.provider.ResultOkImpl
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.channels.ClosedSendChannelException
import kotlinx.coroutines.delay
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.atomic.AtomicInteger


internal class EelPipeImpl() : EelPipe, EelSendChannel<ErrorString>, EelReceiveChannel<ErrorString> {
  private companion object {
    val OK_EOF = ResultOkImpl(ReadResult.EOF)
    val OK_NOT_EOF = ResultOkImpl(ReadResult.NOT_EOF)
  }

  private val channel = Channel<Pair<ByteBuffer, CompletableDeferred<Unit>>>()

  /**
   * Number of bytes currently waiting to be received by [source].
   * This can be used as a hint for [java.io.InputStream.available]
   */
  private val _bytesInQueue = AtomicInteger(0)
  internal val bytesInQueue: Int get() = _bytesInQueue.get()

  override val sink: EelSendChannel<ErrorString> = this
  override val source: EelReceiveChannel<ErrorString> = this
  private val sendLocks = ConcurrentLinkedDeque<CompletableDeferred<Unit>>()

  override suspend fun send(src: ByteBuffer): EelResult<Unit, ErrorString> {
    val remaining = src.remaining()
    val sendLock = CompletableDeferred<Unit>()
    // `send` should return when buffer is read
    sendLocks.add(sendLock)
    try {
      _bytesInQueue.addAndGet(remaining)
      channel.send(Pair(src, sendLock))
      return OK_UNIT
    }
    catch (_: ClosedSendChannelException) {
      closePipe()
      return ERR_CHANNEL_CLOSED
    }
    catch (e: PipeBrokenException) {
      closePipe()
      return e.asErrorResult()
    }
    finally {
      _bytesInQueue.addAndGet(-remaining)
      sendLock.await() //wait for buffer to read
      sendLocks.remove(sendLock)
    }
  }

  override suspend fun receive(dst: ByteBuffer): EelResult<ReadResult, ErrorString> {
    val (src, sendLock) = try {
      channel.receive()
    }
    catch (_: ClosedReceiveChannelException) {
      closePipe()
      return OK_EOF
    }
    catch (e: PipeBrokenException) {
      closePipe()
      return e.asErrorResult()
    }
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
    sendLock.complete(Unit) //buffer read
    return OK_NOT_EOF
  }

  override suspend fun close() {
    if (_bytesInQueue.get() > 0) {
      // We still have some data to be delivered. Let's wait sometime to give change to read it
      delay(200)
    }
    closePipe()
  }


  override fun closePipe(error: Throwable?) {
    if (_bytesInQueue.get() > 0) {
      // We still have some data to be delivered. Let's wait sometime to give change to read it
      Thread.sleep(200)
    }
    channel.close(error?.let { PipeBrokenException(it) })
    channel.cancel() //If there is a coroutine near `send`, it must get an error
    for (deferred in sendLocks) {
      deferred.complete(Unit)
    }
    sendLocks.clear()
  }
}

private class PipeBrokenException(cause: Throwable) : Exception("Pipe was broken with message: ${cause.message}", cause) {

  fun asErrorResult(): EelResult.Error<ErrorString> = ResultErrImpl(message!!)
}