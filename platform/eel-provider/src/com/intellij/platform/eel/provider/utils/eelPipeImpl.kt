// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.provider.utils

import com.intellij.platform.eel.ReadResult
import com.intellij.platform.eel.channels.EelReceiveChannel
import com.intellij.platform.eel.channels.EelSendApi
import com.intellij.platform.eel.channels.EelSendChannel
import com.intellij.platform.eel.channels.EelSendChannelCustomSendWholeBuffer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.channels.ClosedSendChannelException
import kotlinx.coroutines.delay
import java.io.IOException
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.atomic.AtomicInteger

internal class EelPipeImpl() : EelPipe, EelReceiveChannel, EelSendChannelCustomSendWholeBuffer {
  private companion object {
    val OK_EOF = ReadResult.EOF
    val OK_NOT_EOF = ReadResult.NOT_EOF
  }

  @Volatile
  override var closed: Boolean = false
    private set

  private val channel = Channel<Triple<ByteBuffer, CompletableDeferred<Unit>, Boolean>>()

  /**
   * Number of bytes currently waiting to be received by [source].
   * This can be used as a hint for [java.io.InputStream.available]/
   * It only works when [sendWholeBufferCustom] is used, as it is the only way to become responsible for the whole buffer.
   */
  private val _bytesInQueue = AtomicInteger(0)
  internal val bytesInQueue: Int get() = if (channel.isClosedForSend) 0 else _bytesInQueue.get()

  override val sink: EelSendChannel = this
  override val source: EelReceiveChannel = this
  private val sendLocks = ConcurrentLinkedDeque<CompletableDeferred<Unit>>()

  override suspend fun sendWholeBufferCustom(src: ByteBuffer) {
    _bytesInQueue.addAndGet(src.remaining())
    while (src.hasRemaining()) {
      send(src, true)
    }
  }

  @EelSendApi
  override suspend fun send(src: ByteBuffer) {
    return send(src, false)
  }

  private suspend fun send(src: ByteBuffer, decreaseQueueAfterReceive: Boolean) {
    val sendLock = CompletableDeferred<Unit>()
    // `send` should return when buffer is read
    sendLocks.add(sendLock)
    try {
      channel.send(Triple(src, sendLock, decreaseQueueAfterReceive))
      return
    }
    catch (_: ClosedSendChannelException) {
      closePipe()
      throw IOException("Channel is closed")
    }
    catch (e: PipeBrokenException) {
      closePipe()
      throw e
    }
    finally {
      sendLock.await() //wait for buffer to read
      sendLocks.remove(sendLock)
    }
  }

  override suspend fun receive(dst: ByteBuffer): ReadResult {
    val (src, sendLock, decreaseQueueAfterReceive) = try {
      channel.receive()
    }
    catch (_: ClosedReceiveChannelException) {
      closePipe()
      return OK_EOF
    }
    catch (e: PipeBrokenException) {
      closePipe()
      throw e
    }
    val bytesBeforeRead = src.remaining()
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
    // Decreasing the number of bytes should take place on the same thread `receive` is called, as receiver checks number after before
    // sender gets the chance to fix it.
    if (decreaseQueueAfterReceive) {
      val bytesRead = bytesBeforeRead - src.remaining()
      _bytesInQueue.addAndGet(-bytesRead)
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
    closed = true
  }
}

private class PipeBrokenException(cause: Throwable) : IOException("Pipe was broken with message: ${cause.message}", cause)