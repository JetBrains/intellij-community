// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import java.util.concurrent.Callable

interface BuildMessages: System.Logger {
  fun info(message: String)

  fun warning(message: String)

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

  /**
   * Use [spanBuilder]
   */
  @Deprecated(message = "Use [org.jetbrains.intellij.build.telemetry.block]")
  fun block(blockName: String, task: Callable<Unit>)

  /**
   * Use [CompilationContext.notifyArtifactBuilt] instead since it respects [BuildOptions.TEAMCITY_ARTIFACTS_PUBLICATION_STEP]
   */
  fun artifactBuilt(relativeArtifactPath: String)

  fun startWritingFileToBuildLog(artifactPath: String)

  fun reportStatisticValue(key: String, value: String)

  fun reportBuildProblem(description: String, identity: String? = null)

  fun reportBuildNumber(value: String)

  fun cancelBuild(reason: String)

  fun getDebugLog(): String?

  fun close()
}
