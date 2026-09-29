// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.build.output

import java.io.Closeable
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
abstract class LineProcessor : Closeable {
  private var lineBuilder: StringBuilder? = StringBuilder()

  abstract fun process(line: String)

  fun notifyTextAvailable(text: String) {
    if (lineBuilder == null) throw IllegalStateException("The line processor was closed")
    for (c in text) {
      if (c == '\n') {
        if (lineBuilder!!.lastOrNull() == '\r') {
          lineBuilder!!.deleteCharAt(lineBuilder!!.length - 1)
        }
        flushBuffer()
      }
      else {
        lineBuilder!!.append(c)
      }
    }
  }

  override fun close() {
    if (lineBuilder != null) {
      flushBuffer(skipEmptyLine = true)
      lineBuilder = null
    }
  }

  private fun flushBuffer(skipEmptyLine: Boolean = false) {
    val line = lineBuilder!!.toString()
    lineBuilder!!.setLength(0)
    if (!skipEmptyLine || line.isNotEmpty()) {
      process(line)
    }
  }
}
