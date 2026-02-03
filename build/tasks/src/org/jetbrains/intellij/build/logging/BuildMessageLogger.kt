// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.logging

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.intellij.build.BuildScriptsLoggedError

abstract class BuildMessageLogger {
  abstract fun processMessage(message: LogMessage)

  /**
   * Called for a logger of a forked task when the task is completed (i.e. [.processMessage] method won't be called anymore.
   */
  open fun dispose() {}
}

open class LogMessage(val kind: Kind, val text: String) {
  enum class Kind {
    ERROR, WARNING, DEBUG, INFO, PROGRESS, BLOCK_STARTED, BLOCK_FINISHED, ARTIFACT_BUILT, COMPILATION_ERRORS, STATISTICS, BUILD_STATUS, SET_PARAMETER,
    BUILD_PROBLEM, BUILD_STATUS_CHANGED_TO_SUCCESSFUL,
    BUILD_CANCEL, IMPORT_DATA,
    BUILD_NUMBER,
  }
}

@ApiStatus.Internal
class CompilationErrorsLogMessage(@JvmField val compilerName: String, @JvmField val errorMessages: List<String>)
  : LogMessage(Kind.COMPILATION_ERRORS, "$compilerName compilation errors")

@ApiStatus.Internal
class BuildProblemLogMessage(description: String, val identity: String?) : LogMessage(Kind.BUILD_PROBLEM, description)

@ApiStatus.Internal
class ConsoleBuildMessageLogger : BuildMessageLoggerBase() {
  override fun processMessage(message: LogMessage) {
    when (message.kind) {
      // reported by trace exporter
      LogMessage.Kind.BLOCK_STARTED, LogMessage.Kind.BLOCK_FINISHED -> {}
      // failing-fast upon a build problem
      LogMessage.Kind.BUILD_PROBLEM -> throw BuildScriptsLoggedError(message.text)
      LogMessage.Kind.COMPILATION_ERRORS -> {
        check(message is CompilationErrorsLogMessage) {
          "Unexpected compilation errors message type: ${message::class.java.canonicalName}"
        }
        throw BuildScriptsLoggedError(message.errorMessages.joinToString(prefix = "${message.text}:\n", separator = "\n"))
      }
      LogMessage.Kind.INFO,
      LogMessage.Kind.DEBUG,
      LogMessage.Kind.PROGRESS,
      LogMessage.Kind.STATISTICS -> {
        if (verbose) {
          super.processMessage(message)
        }
      }
      else -> super.processMessage(message)
    }
  }

  override fun shouldBePrinted(kind: LogMessage.Kind): Boolean = kind != LogMessage.Kind.DEBUG

  override fun printLine(line: String) {
    println(line)
  }
}

private const val VERBOSE_PROPERTY = "intellij.build.console.messages.verbose"
private const val VERBOSE_DEFAULT = "true"
private val verbose = System.getProperty(VERBOSE_PROPERTY, VERBOSE_DEFAULT).toBooleanStrict()
