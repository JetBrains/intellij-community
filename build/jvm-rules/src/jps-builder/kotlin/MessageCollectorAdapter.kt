package org.jetbrains.bazel.jvm.jps.kotlin

import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import org.jetbrains.annotations.Nls
import org.jetbrains.jps.incremental.CompileContext
import org.jetbrains.jps.incremental.messages.BuildMessage
import org.jetbrains.jps.incremental.messages.CompilerMessage
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSourceLocation
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.config.CompilerRunnerConstants
import org.jetbrains.kotlin.jps.targets.KotlinModuleBuildTarget
import java.io.File
import java.lang.IllegalArgumentException

internal class MessageCollectorAdapter(
  private val context: CompileContext,
  private val span: Span,
  private val kotlinTarget: KotlinModuleBuildTarget<*>? = null,
) : MessageCollector {
  private var hasErrors = false
  @JvmField
  val filesWithErrors = LinkedHashSet<String>()

  override fun report(severity: CompilerMessageSeverity, @Nls message: String, location: CompilerMessageSourceLocation?) {
    if (severity.isError) {
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
      if (location != null && kotlinTarget != null && kotlinTarget.isFromIncludedSourceRoot(File(location.path))) {
        val moduleName = kotlinTarget.module.name
        prefix += "[$moduleName] "
      }
      if (severity.isError) {
        location?.let { filesWithErrors.add(it.path) }
      }

      context.processMessage(
        CompilerMessage(
          CompilerRunnerConstants.KOTLIN_COMPILER_NAME,
          kind,
          prefix + message,
          location?.path,
          -1, -1, -1,
          location?.line?.toLong() ?: -1,
          location?.column?.toLong() ?: -1
        )
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