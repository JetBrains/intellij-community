// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools.build.bazel.jvmIncBuilder.impl

import com.intellij.tools.build.bazel.jvmIncBuilder.DiagnosticSink
import com.intellij.tools.build.bazel.jvmIncBuilder.Message
import org.jetbrains.annotations.Nls
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSourceLocation
import org.jetbrains.kotlin.cli.common.messages.MessageCollector

class KotlinMessageCollector(
  private val diagnosticSink: DiagnosticSink,
  private val myOwner: KotlinCompilerRunner,
) : MessageCollector {
  private var hasErrors = false

  override fun report(severity: CompilerMessageSeverity, @Nls message: String, location: CompilerMessageSourceLocation?) {
    hasErrors = hasErrors || severity.isError

    var prefix = ""
    if (severity == CompilerMessageSeverity.EXCEPTION) {
      prefix = "[Internal Error] "
    }

    val kind = kind(severity)
    if (kind != null) {
      diagnosticSink.report(Message.create(myOwner, kind, prefix + message))
    }
  }

  override fun clear() {
    hasErrors = false
  }

  override fun hasErrors(): Boolean = hasErrors

  private fun kind(severity: CompilerMessageSeverity): Message.Kind? {
    return when (severity) {
      CompilerMessageSeverity.INFO -> Message.Kind.INFO
      CompilerMessageSeverity.ERROR, CompilerMessageSeverity.EXCEPTION -> Message.Kind.ERROR
      CompilerMessageSeverity.WARNING, CompilerMessageSeverity.STRONG_WARNING, CompilerMessageSeverity.FIXED_WARNING -> Message.Kind.WARNING
      CompilerMessageSeverity.LOGGING -> null
      else -> throw IllegalArgumentException("Unsupported severity: $severity")
    }
  }
}
