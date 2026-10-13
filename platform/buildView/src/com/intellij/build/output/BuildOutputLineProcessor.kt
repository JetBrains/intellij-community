// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.build.output

import com.intellij.build.events.OutputId
import org.jetbrains.annotations.ApiStatus.Internal
import java.util.concurrent.atomic.AtomicReference

@Internal
class BuildOutputLineProcessor(
  private val multicaster: LineMulticaster,
) {

  private val buffer = AtomicReference(BuildOutputBuffer())

  suspend fun notifyTextAvailable(text: BuildOutputText) {
    val lines = text.text.split('\n')
    for (line in lines.dropLast(1)) {
      notifyLineOrPrefixAvailable(text.ids, line, isLine = true)
    }
    val linePrefix = lines.last()
    if (linePrefix.isNotEmpty()) {
      notifyLineOrPrefixAvailable(text.ids, linePrefix, isLine = false)
    }
  }

  private suspend fun notifyLineOrPrefixAvailable(outputIds: List<OutputId>, outputText: String, isLine: Boolean) {
    val buffer = buffer.get() ?: throw ClosedException()
    buffer.outputIds.addAll(outputIds)
    val end = outputText.indexOfLast { it != '\r' } + 1
    if (end > 0) {
      val carriageReturnIndex = outputText.lastIndexOf('\r', end - 1)
      if (carriageReturnIndex >= 0 || buffer.hasPendingCarriageReturn) {
        buffer.outputText.setLength(0)
      }
      buffer.outputText.append(outputText, carriageReturnIndex + 1, end)
      buffer.hasPendingCarriageReturn = false
    }
    if (end < outputText.length) {
      buffer.hasPendingCarriageReturn = true
    }
    if (isLine) {
      buffer.flush()
    }
  }

  suspend fun close() {
    val buffer = buffer.getAndSet(null) ?: throw ClosedException()
    buffer.flush(skipEmptyLine = true)
  }

  private inner class BuildOutputBuffer {

    val outputIds = ArrayList<Any>()
    val outputText = StringBuilder()
    var hasPendingCarriageReturn = false

    suspend fun flush(skipEmptyLine: Boolean = false) {
      if (!skipEmptyLine || outputText.isNotEmpty()) {
        multicaster.notifyLineAvailable(BuildOutputText.createOutputText(
          outputIds.toList(),
          outputText.append('\n').toString()
        ))
        outputIds.clear()
        outputText.setLength(0)
        hasPendingCarriageReturn = false
      }
    }
  }

  private class ClosedException : IllegalStateException()

  fun interface LineMulticaster {

    suspend fun notifyLineAvailable(line: BuildOutputText)
  }
}
