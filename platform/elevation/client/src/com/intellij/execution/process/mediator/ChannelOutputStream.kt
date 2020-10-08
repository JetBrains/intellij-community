// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.process.mediator

import com.google.protobuf.ByteString
import kotlinx.coroutines.channels.ClosedSendChannelException
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.sendBlocking
import java.io.IOException
import java.io.OutputStream
import java.util.*

class ChannelOutputStream(private val writeChannel: SendChannel<ByteString>) : OutputStream() {

  override fun write(b: Int) {
    write(ByteArray(1) { b.toByte() })
  }

  override fun write(b: ByteArray, off: Int, len: Int) {
    Objects.checkFromIndexSize(off, len, b.size)
    if (len == 0) return

    val byteString = ByteString.copyFrom(b, 0, len)
    try {
      writeChannel.sendBlocking(byteString)
    }
    catch (e: ClosedSendChannelException) {
      throw IOException("Stream closed", e)
    }
  }

  override fun flush() {}

  override fun close() {
    writeChannel.close()
  }
}