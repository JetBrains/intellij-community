// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel

import com.intellij.platform.eel.ReadResult.EOF
import com.intellij.platform.eel.ReadResult.NOT_EOF


/**
 * When reading from a channel/file/socket/pipe/stream, you might end up with:
 * * [EOF]: "no data was read" because you've reached the end: semantics is barely the same as `-1` in many APIs.
 * * [NOT_EOF]: some data might be read because it isn't the end (yet).
 *
 * To see how much bytes were read, compare [java.nio.ByteBuffer.position] with the one you had before read, i.e:
 * ```kotlin
 * var before = 0
 * while(file.read(buffer) != EOF) {
 *   println("I read ${buffer.position() - before} bytes")
 *   before = buffer.position()
 *   assert(buffer.hasRemaining) {"Oops, the buffer is full"}
 * }
 * ```
 */
enum class ReadResult {
  EOF,
  NOT_EOF;

  companion object {
    /**
     * ```kotlin
     *  fromNumberOfReadBytes(stream.read(buffer))
     *  ```
     */
    fun fromNumberOfReadBytes(bytesRead: Int): ReadResult = if (bytesRead < -1) {
      throw IllegalArgumentException("Number of bytes read must be in -1..INT_MAX, can't be $bytesRead")
    }
    else {
      if (bytesRead == -1) EOF else NOT_EOF
    }
  }
}