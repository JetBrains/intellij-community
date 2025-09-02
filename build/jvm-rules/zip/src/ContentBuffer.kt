// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.io

import io.netty.buffer.ByteBuf
import java.io.EOFException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel

fun writeToFileChannelFully(channel: FileChannel, position: Long, buffer: ByteBuf): Long {
  var toWrite = buffer.readableBytes()
  var currentPosition = position
  while (toWrite > 0) {
    val n = buffer.readBytes(channel, currentPosition, toWrite)
    if (n < 0) {
      throw EOFException("Unexpected end of file while writing to FileChannel")
    }
    toWrite -= n
    currentPosition += n
  }
  return currentPosition
}

fun writeToFileChannelFully(channel: FileChannel, data: ByteBuffer, position: Long = 0) {
  var currentPosition = position
  do {
    currentPosition += channel.write(data, currentPosition)
  }
  while (data.hasRemaining())
}

inline fun <T> ByteBuf.use(block: (ByteBuf) -> T): T {
  try {
    return block(this)
  }
  finally {
    release()
  }
}