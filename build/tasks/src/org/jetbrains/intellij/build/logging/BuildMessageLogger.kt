// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.logging

import org.jetbrains.annotations.ApiStatus

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
    BUILD_CANCEL,
  }
}

@ApiStatus.Internal
class CompilationErrorsLogMessage(@JvmField val compilerName: String, @JvmField val errorMessages: List<String>)
  : LogMessage(Kind.COMPILATION_ERRORS, "$compilerName compilation errors")

@ApiStatus.Internal
class BuildProblemLogMessage(description: String, val identity: String?) : LogMessage(Kind.BUILD_PROBLEM, description)

@ApiStatus.Internal
class ConsoleBuildMessageLogger : BuildMessageLoggerBase() {
  companion object {
    @JvmField
    val FACTORY: () -> BuildMessageLogger = ::ConsoleBuildMessageLogger
  }

  override fun processMessage(message: LogMessage) {
    // reported by trace exporter
    if (message.kind != LogMessage.Kind.BLOCK_STARTED && message.kind != LogMessage.Kind.BLOCK_FINISHED) {
      super.processMessage(message)
    }
  }

  override fun shouldBePrinted(kind: LogMessage.Kind) = kind != LogMessage.Kind.DEBUG

  override fun printLine(line: String) {
    println(line)
  }
}