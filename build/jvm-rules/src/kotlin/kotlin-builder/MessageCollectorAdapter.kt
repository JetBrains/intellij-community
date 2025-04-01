// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.bazel.jvm.kotlin

import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet
import org.jetbrains.annotations.Nls
import org.jetbrains.bazel.jvm.worker.core.BazelCompileContext
import org.jetbrains.jps.incremental.messages.BuildMessage
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSourceLocation
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.config.CompilerRunnerConstants
import org.jetbrains.kotlin.jps.targets.KotlinModuleBuildTarget
import java.io.File
import java.nio.file.Path

internal class MessageCollectorAdapter(
  private val context: BazelCompileContext,
  private val span: Span,
  private val kotlinTarget: KotlinModuleBuildTarget<*>?,
  private val skipWarns: Boolean,
) : MessageCollector {
  private var hasErrors = false
  @JvmField
  val filesWithErrors = ObjectLinkedOpenHashSet<Path>()

  override fun report(severity: CompilerMessageSeverity, @Nls message: String, location: CompilerMessageSourceLocation?) {
    if (severity == CompilerMessageSeverity.WARNING) {
      if (skipWarns) {
        return
      }
    }
    else if (severity.isError) {
      hasErrors = true
    }

    var prefix = ""
    if (severity == CompilerMessageSeverity.EXCEPTION) {
      prefix = CompilerRunnerConstants.INTERNAL_ERROR_PREFIX
    }

    val kind = kind(severity)
    if (kind == null) {
      if (span.isRecording) {
        span.addEvent(message, Attributes.of(
          AttributeKey.stringKey("path"), if (location == null) "" else "${location.path}:${location.line}:${location.column}: ",
        ))
      }
    }
    else {
      // report target when cross-compiling common files
      if (location != null) {
        if (kotlinTarget != null && kotlinTarget.isFromIncludedSourceRoot(File(location.path))) {
          prefix += "[${kotlinTarget.module.name}] "
        }
        if (severity.isError) {
          filesWithErrors.add(Path.of(location.path))
        }
      }

      context.compilerMessage(
        kind = kind,
        message = prefix + message,
        sourcePath = location?.path,
        line = location?.line ?: -1,
        column = location?.column ?: -1
      )
    }
  }

  override fun clear() {
    hasErrors = false
  }

  override fun hasErrors(): Boolean = hasErrors
}

private fun kind(severity: CompilerMessageSeverity): BuildMessage.Kind? {
  return when (severity) {
    CompilerMessageSeverity.INFO -> BuildMessage.Kind.INFO
    CompilerMessageSeverity.ERROR, CompilerMessageSeverity.EXCEPTION -> BuildMessage.Kind.ERROR
    CompilerMessageSeverity.WARNING, CompilerMessageSeverity.STRONG_WARNING -> BuildMessage.Kind.WARNING
    CompilerMessageSeverity.LOGGING -> null
    else -> throw IllegalArgumentException("Unsupported severity: $severity")
  }
}