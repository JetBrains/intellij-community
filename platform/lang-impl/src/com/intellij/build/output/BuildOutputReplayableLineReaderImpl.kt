// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.build.output

import org.jetbrains.annotations.ApiStatus.Internal
import java.util.LinkedList

@Internal
class BuildOutputReplayableLineReaderImpl(
  linesBufferSize: Int = 64,
  private val pushBackBufferSize: Int = 50,
) : BuildOutputReplayableLineReader {

  private val reader = BuildOutputLineReaderImpl(linesBufferSize)

  // Most-recently-read lines; index 0 is the newest.
  private val readLinesBuffer = LinkedList<String>()

  // >= 0 means "we are replaying from the buffer at this index"; -1 means "read from reader".
  private var readLinesBufferPosition = -1

  override suspend fun notifyTextAvailable(text: CharSequence) {
    reader.notifyTextAvailable(text)
  }

  override suspend fun readLine(): String? {
    if (readLinesBufferPosition >= 0) {
      val line = readLinesBuffer[readLinesBufferPosition]
      readLinesBufferPosition--
      return line
    }
    val line = reader.readLine() ?: return null
    readLinesBuffer.addFirst(line)
    if (readLinesBuffer.size > pushBackBufferSize) {
      readLinesBuffer.removeLast()
    }
    return line
  }

  override fun pushBack(numberOfLines: Int) {
    readLinesBufferPosition += numberOfLines
    if (readLinesBufferPosition >= pushBackBufferSize) {
      readLinesBufferPosition = pushBackBufferSize - 1
    }
  }

  override suspend fun close() {
    reader.close()
  }
}
