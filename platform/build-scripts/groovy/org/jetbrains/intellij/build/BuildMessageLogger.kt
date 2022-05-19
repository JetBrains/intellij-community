// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

abstract class BuildMessageLogger {
  abstract fun processMessage(message: LogMessage?)

  /**
   * Called for a logger of a forked task when the task is completed (i.e. [.processMessage] method won't be called anymore.
   */
  open fun dispose() {}
}

open class LogMessage(val kind: Kind, val text: String) {
  enum class Kind {
    ERROR, WARNING, DEBUG, INFO, PROGRESS, BLOCK_STARTED, BLOCK_FINISHED, ARTIFACT_BUILT, COMPILATION_ERRORS, STATISTICS, BUILD_STATUS, SET_PARAMETER
  }
}

internal class CompilationErrorsLogMessage(@JvmField val compilerName: String, @JvmField val errorMessages: List<String>)
  : LogMessage(Kind.COMPILATION_ERRORS, "$compilerName compilation errors")
