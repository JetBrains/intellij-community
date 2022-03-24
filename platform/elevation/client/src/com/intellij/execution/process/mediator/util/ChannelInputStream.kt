// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.process.mediator.util

import com.google.protobuf.ByteString
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.onClosed
import kotlinx.coroutines.runBlocking
import java.io.IOException
import java.io.InputStream
import java.util.*

class ChannelInputStream(private val readChannel: ReceiveChannel<ByteString>) : InputStream() {
  private var carryChunk: ByteString = ByteString.EMPTY

  override fun read(): Int {
    val byteArray = ByteArray(1)
    val n = this.read(byteArray)
    return if (n == 1) (byteArray[0].toInt() and 0xff) else -1
  }

  override fun read(b: ByteArray, off: Int, len: Int): Int = synchronized(this) {
    Objects.checkFromIndexSize(off, len, b.size)
    if (len == 0) return 0

    if (drainChannel().isEmpty) {
      carryChunk = try {
        runBlocking { readChannel.receive() }
      }
      catch (e: CancellationException) {
        throw IOException(e)
      }
      catch (e: ClosedReceiveChannelException) {
        return -1 // EOF
      }
    }

    val copyChunk = if (carryChunk.size() > len) carryChunk.substring(0, len) else carryChunk
    copyChunk.copyTo(b, off)
    carryChunk = if (carryChunk.size() > len) carryChunk.substring(len) else ByteString.EMPTY

    return copyChunk.size()
  }

  override fun available(): Int = synchronized(this) {
    return drainChannel().size()
  }

  /** Does nothing in case EOF is reached, throws IOException if the stream was closed. */
  private fun drainChannel(): ByteString {
    var chunk = carryChunk
    try {
      do {
        val nextChunk = readChannel.tryReceive()
                          .onClosed { if (it is CancellationException) throw IOException(it) }
                          .getOrNull() ?: break
        chunk = chunk.concat(nextChunk)
      }
      while (true)
    }
    finally {
      carryChunk = chunk
    }
    return chunk
  }

  override fun close() {
    readChannel.cancel()
  }
}