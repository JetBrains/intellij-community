/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.diagnostic.hprof.util

import java.io.Closeable
import java.nio.ByteBuffer
import java.nio.channels.FileChannel

class FileChannelBackedWriteBuffer(
  private val channel: FileChannel,
  private val closeOutput: Boolean = false
) : Closeable {
  private val tempBuf = ByteBuffer.allocateDirect(70 * 1024)

  private var position: Int = 0
  private var closed = false

  override fun close() {
    if (!closed) {
      closed = true
      try {
        flushBuffer()
      }
      finally {
        if (closeOutput) {
          channel.close()
        }
      }
    }
  }

  private fun flushBuffer() {
    tempBuf.flip()
    channel.write(tempBuf)
    tempBuf.clear()
  }

  fun writeLong(value: Long) {
    if (tempBuf.remaining() < 8) {
      flushBuffer()
    }
    tempBuf.putLong(value)
    position += 8
  }

  fun writeInt(value: Int) {
    if (tempBuf.remaining() < 4) {
      flushBuffer()
    }
    tempBuf.putInt(value)
    position += 4
  }

  fun writeShort(value: Short) {
    if (tempBuf.remaining() < 2) {
      flushBuffer()
    }
    tempBuf.putShort(value)
    position += 2
  }

  fun writeByte(value: Byte) {
    if (tempBuf.remaining() < 1) {
      flushBuffer()
    }
    tempBuf.put(value)
    position += 1
  }

  fun writeNonNegativeLEB128Int(value: Int) {
    assert(value >= 0)
    var v = value
    do {
      var b = v and 0x7f
      v = v shr 7
      if (v != 0) {
        b = b or 0x80
      }
      writeByte(b.toByte())
    }
    while (v != 0)
  }

  fun writeString(s: String) {
    val bytes = s.toByteArray(Charsets.UTF_8)
    if (bytes.size > Short.MAX_VALUE)
      throw IllegalArgumentException("String too long.")
    writeShort(s.length.toShort())
    if (tempBuf.remaining() < bytes.size) {
      flushBuffer()
    }
    tempBuf.put(bytes)
    position += 2 + bytes.size
  }

  fun writeBytes(bytes: ByteBuffer) {
    val remaining = bytes.remaining()
    if (remaining > tempBuf.remaining()) {
      flushBuffer()
      channel.write(bytes)
    }
    else {
      tempBuf.put(bytes)
    }
    position += remaining
  }


  fun position(): Int {
    return position
  }

}