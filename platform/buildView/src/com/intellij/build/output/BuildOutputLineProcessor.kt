// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.build.output

import org.jetbrains.annotations.ApiStatus.Internal
import java.util.concurrent.atomic.AtomicReference

@Internal
class BuildOutputLineProcessor(
  private val multicaster: LineMulticaster,
) {

  private val buffer = AtomicReference(BuildOutputBuffer())

  suspend fun notifyTextAvailable(text: CharSequence) {
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
    val buffer = buffer.get() ?: throw ClosedException()
    buffer.text.append(text)
    if (isLine) {
      buffer.flush()
    }
  }

  suspend fun close() {
    val buffer = buffer.getAndSet(null) ?: throw ClosedException()
    buffer.flush(skipEmptyLine = true)
  }

  private inner class BuildOutputBuffer {
    val text = StringBuilder()

    suspend fun flush(skipEmptyLine: Boolean = false) {
      if (!skipEmptyLine || text.isNotEmpty()) {
        multicaster.notifyLineAvailable(text.toString())
        text.setLength(0)
      }
    }
  }

  private class ClosedException : IllegalStateException()

  fun interface LineMulticaster {

    suspend fun notifyLineAvailable(text: String)
  }
}
