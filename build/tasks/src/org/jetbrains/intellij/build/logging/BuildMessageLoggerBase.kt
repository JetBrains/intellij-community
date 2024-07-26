// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
        Span.current().addEvent("artifact built: ${message.text}")
      }
      LogMessage.Kind.COMPILATION_ERRORS -> {
        val errorsString = (message as CompilationErrorsLogMessage).errorMessages.joinToString(separator = "\n")
        Span.current().addEvent("compilation errors (${message.compilerName}):\n$errorsString")
      }
      LogMessage.Kind.BUILD_CANCEL, LogMessage.Kind.BUILD_PROBLEM -> {
        throw BuildScriptsLoggedError(message.text)
      }
      else -> {
        if (shouldBePrinted(message.kind)) {
          Span.current().addEvent(message.text)
        }
      }
    }
  }

  protected open fun shouldBePrinted(kind: LogMessage.Kind) = true

  protected abstract fun printLine(line: String)
}
