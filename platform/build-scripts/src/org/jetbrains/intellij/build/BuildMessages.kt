// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import java.util.concurrent.Callable

interface BuildMessages: System.Logger {
  fun info(message: String)

  fun warning(message: String)

  /**
   * Print `message` to <output-root>/log/debug.log file. This log file will also include 'info' and 'warning' messages.
   */
  fun debug(message: String)

  /**
   * Report an error and stop the build process
   */
  fun error(message: String)

  fun error(message: String, cause: Throwable)

  fun compilationErrors(compilerName: String, messages: List<String>)

  fun progress(message: String)

  fun buildStatus(message: String)

  fun changeBuildStatusToSuccess(message: String)

  fun setParameter(parameterName: String, value: String)

  fun block(blockName: String, task: Callable<Unit>)

  fun artifactBuilt(relativeArtifactPath: String)

  fun reportStatisticValue(key: String, value: String)

  fun reportBuildProblem(description: String, identity: String? = null)

  fun cancelBuild(reason: String)
}
