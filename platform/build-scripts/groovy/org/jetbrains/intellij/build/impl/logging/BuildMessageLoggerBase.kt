// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl.logging

import org.jetbrains.intellij.build.BuildMessageLogger
import org.jetbrains.intellij.build.CompilationErrorsLogMessage
import org.jetbrains.intellij.build.LogMessage

abstract class BuildMessageLoggerBase : BuildMessageLogger() {
  private var indent = 0

  override fun processMessage(message: LogMessage) {
    when (message.kind) {
      LogMessage.Kind.BLOCK_STARTED -> {
        printMessage(message.text)
        indent++
      }
       LogMessage.Kind.BLOCK_FINISHED -> {
         indent--
       }
      LogMessage.Kind.ARTIFACT_BUILT -> {
        printMessage("Artifact built: ${message.text}")
      }
      LogMessage.Kind.COMPILATION_ERRORS -> {
        val errorsString = (message as CompilationErrorsLogMessage).errorMessages.joinToString(separator = "\n")
        printMessage("Compilation errors (${message.compilerName}):\n$errorsString")
      }
      else -> {
        if (shouldBePrinted(message.kind)) {
          printMessage(message.text)
        }
      }
    }
  }

  protected open fun shouldBePrinted(kind: LogMessage.Kind) = true

  private fun printMessage(message: String) {
    message.lineSequence().forEach {
      printLine(" ".repeat(indent) + it)
    }
  }

  protected abstract fun printLine(line: String)
}
