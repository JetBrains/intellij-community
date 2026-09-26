// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.logging

import io.opentelemetry.api.trace.Span
import org.jetbrains.intellij.build.BuildScriptsLoggedError

abstract class BuildMessageLoggerBase : BuildMessageLogger() {
  private var indent = 0

  override fun processMessage(message: LogMessage) {
    when (message.kind) {
      LogMessage.Kind.BLOCK_STARTED -> {
        for (line in message.text.lineSequence()) {
          printLine(" ".repeat(indent) + line)
        }
        indent++
      }
      LogMessage.Kind.BLOCK_FINISHED -> {
        indent--
      }
      LogMessage.Kind.ARTIFACT_BUILT -> {
        addEvent("artifact built: ${message.text}")
      }
      LogMessage.Kind.COMPILATION_ERRORS -> {
        // reported as span event
      }
      LogMessage.Kind.BUILD_CANCEL -> throw BuildScriptsLoggedError(message.text)
      else -> {
        if (shouldBePrinted(message.kind)) {
          addEvent(message.text)
        }
      }
    }
  }

  /** True when [processMessage] posts a message of this [kind] to the active span. A secondary logger uses it to skip the duplicate event. */
  fun addsSpanEvent(kind: LogMessage.Kind): Boolean = when (kind) {
    LogMessage.Kind.ARTIFACT_BUILT -> true
    LogMessage.Kind.BLOCK_STARTED, LogMessage.Kind.BLOCK_FINISHED,
    LogMessage.Kind.COMPILATION_ERRORS, LogMessage.Kind.BUILD_CANCEL -> false
    else -> shouldBePrinted(kind)
  }

  protected open fun addEvent(text: String) {
    val currentSpan = Span.current()
    if (currentSpan.spanContext.isValid) {
      currentSpan.addEvent(text)
    }
    else {
      // No current span is defined. Most likely, top-level span is missing
      printLine(text)
    }
  }

  protected open fun shouldBePrinted(kind: LogMessage.Kind) = true

  protected abstract fun printLine(line: String)
}
