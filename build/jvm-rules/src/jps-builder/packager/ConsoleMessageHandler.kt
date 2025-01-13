// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("HardCodedStringLiteral")

package org.jetbrains.bazel.jvm.jps

import org.jetbrains.jps.incremental.MessageHandler
import org.jetbrains.jps.incremental.messages.BuildMessage
import org.jetbrains.jps.incremental.messages.CompilerMessage

class ConsoleMessageHandler(
  @PublishedApi @JvmField internal val out: Appendable,
  @JvmField val isDebugEnabled: Boolean,
) : MessageHandler {
  @Volatile
  private var hasErrors = false

  fun resetState() {
    hasErrors = false
  }

  fun warn(message: String) {
    out.appendLine("WARN: $message")
  }

  fun error(message: String) {
    out.appendLine("ERROR: $message")
  }

  fun info(message: String) {
    out.appendLine("INFO: $message")
  }

  fun debug(message: String) {
    out.appendLine("DEBUG: $message")
  }

  inline fun measureTime(label: String, block: () -> Unit) {
    val duration = kotlin.time.measureTime(block)
    out.appendLine("TIME: $label: $duration")
  }

  override fun processMessage(message: BuildMessage) {
    val messageText = when (message) {
      is CompilerMessage -> {
        when {
          message.sourcePath == null -> message.messageText
          message.line < 0 -> message.sourcePath + ": " + message.messageText
          else -> message.sourcePath + "(" + message.line + ":" + message.column + "): " + message.messageText
        }
      }

      else -> message.messageText
    }

    if (messageText.isEmpty()) {
      return
    }

    if (message.kind == BuildMessage.Kind.ERROR) {
      out.appendLine("Error: $messageText")
      hasErrors = true
    }
    else if (message.kind !== BuildMessage.Kind.PROGRESS || !messageText.startsWith("Compiled") && !messageText.startsWith("Copying")) {
      out.appendLine(messageText)
    }
  }

  fun hasErrors(): Boolean = hasErrors
}