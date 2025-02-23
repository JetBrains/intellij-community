// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("HardCodedStringLiteral", "UnstableApiUsage")

package org.jetbrains.bazel.jvm.jps.impl

import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.Tracer
import org.jetbrains.jps.incremental.MessageHandler
import org.jetbrains.jps.incremental.messages.BuildMessage
import org.jetbrains.jps.incremental.messages.BuildMessage.Kind
import org.jetbrains.jps.incremental.messages.CompilerMessage
import org.jetbrains.jps.incremental.messages.CustomBuilderMessage
import org.jetbrains.jps.incremental.messages.ProgressMessage
import org.jetbrains.jps.incremental.storage.PathTypeAwareRelativizer
import org.jetbrains.jps.incremental.storage.RelativePathType

internal class RequestLog(
  @JvmField val out: Appendable,
  @JvmField val parentSpan: Span,
  @JvmField val tracer: Tracer,
  @JvmField val relativizer: PathTypeAwareRelativizer,
) : MessageHandler {
  @Volatile
  private var hasErrors = false

  fun resetState() {
    hasErrors = false
  }

  fun compilerMessage(kind: Kind, message: String, sourcePath: String? = null, line: Int = -1, column: Int = -1) {
    val m = if (sourcePath != null) {
      val sourcePath = relativizer.toRelative(sourcePath, RelativePathType.SOURCE)
      if (line < 0) {
        "$sourcePath: $message"
      }
      else {
        "$sourcePath($line:$column): $message"
      }
    }
    else {
      message
    }

    if (m.isEmpty()) {
      return
    }
    if (kind == Kind.ERROR) {
      parentSpan.addEvent("compilation error", Attributes.of(AttributeKey.stringKey("message"), m))
      out.appendLine("Error: $m")
      hasErrors = true
    }
    else {
      out.appendLine(m)
    }
  }

  fun compilerMessage(message: CompilerMessage) {
    var sourcePath = message.sourcePath
    var messageText = message.messageText
    if (sourcePath != null) {
      sourcePath = relativizer.toRelative(sourcePath, RelativePathType.SOURCE)
      messageText = if (message.line < 0) {
        "$sourcePath: $messageText"
      }
      else {
        "$sourcePath(${message.line}:${message.column}): $messageText"
      }
    }

    if (!messageText.isEmpty()) {
      doReport(messageText, message)
    }
  }

  private fun doReport(messageText: String, message: BuildMessage) {
    if (message.kind == Kind.ERROR) {
      parentSpan.addEvent("compilation error", Attributes.of(AttributeKey.stringKey("message"), messageText))
      out.appendLine("Error: $messageText")
      hasErrors = true
    }
    else if (!messageText.startsWith("Compiled") && !messageText.startsWith("Copying")) {
      out.appendLine(messageText)
    }
  }

  override fun processMessage(message: BuildMessage) {
    val messageText = when (message) {
      is CompilerMessage -> {
        var sourcePath = message.sourcePath
        val messageText = message.messageText
        if (sourcePath == null) {
          messageText
        }
        else {
          sourcePath = relativizer.toRelative(sourcePath, RelativePathType.SOURCE)
          if (message.line < 0) {
            "$sourcePath: $messageText"
          }
          else {
            "$sourcePath(${message.line}:${message.column}): $messageText"
          }
        }
      }
      is ProgressMessage -> return
      is CustomBuilderMessage -> {
        if (message.messageType == "processed module") {
          return
        }
        else {
          message.messageText
        }
      }

      else -> message.messageText
    }

    if (!messageText.isEmpty()) {
      doReport(messageText, message)
    }
  }

  fun hasErrors(): Boolean = hasErrors
}