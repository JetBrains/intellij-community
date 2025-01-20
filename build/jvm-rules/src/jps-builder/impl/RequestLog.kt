@file:Suppress("HardCodedStringLiteral")

package org.jetbrains.bazel.jvm.jps.impl

import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.Tracer
import org.jetbrains.jps.incremental.MessageHandler
import org.jetbrains.jps.incremental.messages.BuildMessage
import org.jetbrains.jps.incremental.messages.CompilerMessage
import org.jetbrains.jps.incremental.messages.ProgressMessage

internal class RequestLog(
  @JvmField val out: Appendable,
  @JvmField val parentSpan: Span,
  @JvmField val tracer: Tracer,
) : MessageHandler {
  @Volatile
  private var hasErrors = false

  fun resetState() {
    hasErrors = false
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
      is ProgressMessage -> return

      else -> message.messageText
    }

    if (messageText.isEmpty()) {
      return
    }

    if (message.kind == BuildMessage.Kind.ERROR) {
      parentSpan.addEvent("compilation error", Attributes.of(AttributeKey.stringKey("message"), messageText))
      out.appendLine("Error: $messageText")
      hasErrors = true
    }
    else if (!messageText.startsWith("Compiled") && !messageText.startsWith("Copying")) {
      out.appendLine(messageText)
    }
  }

  fun hasErrors(): Boolean = hasErrors
}