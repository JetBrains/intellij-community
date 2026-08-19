// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.build.output

import kotlinx.coroutines.channels.Channel
import org.jetbrains.annotations.ApiStatus.Internal
import java.util.concurrent.atomic.AtomicReference

@Internal
class BuildOutputLineReaderImpl(
  linesBufferSize: Int = 64,
) : BuildOutputLineReader {

  private val buffer = AtomicReference(BuildOutputBuffer())

  private val channel = Channel<String>(linesBufferSize)

  override suspend fun notifyTextAvailable(text: CharSequence) {
    val lines = text.split('\n')
    for (line in lines.dropLast(1)) {
      notifyLineOrPrefixAvailable(line.removeSuffix("\r"), isLine = true)
    }
    val linePrefix = lines.last()
    if (linePrefix.isNotEmpty()) {
      notifyLineOrPrefixAvailable(linePrefix, isLine = false)
    }
  }

  private suspend fun notifyLineOrPrefixAvailable(text: CharSequence, isLine: Boolean) {
    val buffer = buffer.get() ?: throw ReaderClosedException()
    buffer.text.append(text)
    if (isLine) {
      buffer.flush()
    }
  }

  override suspend fun readLine(): String? {
    return channel.receiveCatching().getOrNull()
  }

  override suspend fun close() {
    val buffer = buffer.getAndSet(null) ?: throw ReaderClosedException()
    buffer.flush(skipEmptyLine = true)
    channel.close()
  }

  private inner class BuildOutputBuffer {
    val text = StringBuilder()

    suspend fun flush(skipEmptyLine: Boolean = false) {
      if (!skipEmptyLine || text.isNotEmpty()) {
        channel.send(text.toString())
        text.setLength(0)
      }
    }
  }

  private class ReaderClosedException : IllegalStateException("The reader is closed")
}
