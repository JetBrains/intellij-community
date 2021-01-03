// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.process.mediator.util

import com.google.protobuf.ByteString
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.ClosedSendChannelException
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.runBlocking
import java.io.IOException
import java.io.OutputStream
import java.util.*
import java.util.concurrent.atomic.AtomicLong

@Suppress("EXPERIMENTAL_API_USAGE")
class ChannelOutputStream(private val writeChannel: SendChannel<ByteString>,
                          private val ackFlow: StateFlow<Long?>) : OutputStream() {
  private val job = Job()
  private val writeCounter = AtomicLong()

  override fun write(b: Int) {
    write(ByteArray(1) { b.toByte() })
  }

  override fun write(b: ByteArray, off: Int, len: Int) {
    Objects.checkFromIndexSize(off, len, b.size)
    if (len == 0) return

    val byteString = ByteString.copyFrom(b, 0, len)
    try {
      runBlocking(job) {
        writeChannel.send(byteString)
        writeCounter.incrementAndGet()
      }
    }
    catch (e: CancellationException) {
      throw IOException("Stream closed", e)
    }
    catch (e: ClosedSendChannelException) {
      throw IOException("Stream closed", e)
    }
  }

  override fun flush() {
    val ackCount = ackFlow.value
    val writeCount = writeCounter.get()

    if (ackCount != null) check(ackCount <= writeCount) { "writeCount: $writeCount; ackCount: $ackCount" }

    if (ackCount != null && ackCount < writeCount) {
      synchronized(this) {
        try {
          runBlocking(job) {
            ackFlow.takeWhile { it != null && it < writeCounter.get() }.collect()
          }
        }
        catch (e: CancellationException) {
          throw IOException("Stream closed", e)
        }
      }
    }
    if (ackFlow.value == null || job.isCancelled) {
      throw IOException("Stream closed")
    }
  }

  override fun close() {
    writeChannel.close()
    job.cancel()
  }
}