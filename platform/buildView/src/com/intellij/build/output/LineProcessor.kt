// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.build.output

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
abstract class LineProcessor : AutoCloseable {

  private var buffer: StringBuilder? = StringBuilder()

  abstract fun process(line: String)

  fun notifyTextAvailable(text: String) {
    val lineBuilder = buffer ?: throw IllegalStateException("The line processor was closed")
    for (c in text) {
      if (c == '\n') {
        if (lineBuilder.lastOrNull() == '\r') {
          lineBuilder.deleteCharAt(lineBuilder.length - 1)
        }
        lineBuilder.flushBuffer()
      }
      else {
        lineBuilder.append(c)
      }
    }
  }

  override fun close() {
    buffer?.flushBuffer(skipEmptyLine = true)
    buffer = null
  }

  private fun StringBuilder.flushBuffer(skipEmptyLine: Boolean = false) {
    val line = this.toString()
    this.setLength(0)
    if (!skipEmptyLine || line.isNotEmpty()) {
      process(line)
    }
  }
}
