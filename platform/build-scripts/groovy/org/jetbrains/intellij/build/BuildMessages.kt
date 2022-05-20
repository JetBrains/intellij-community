// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

interface BuildMessages: System.Logger {
  fun info(message: String)

  fun warning(message: String)

  /**
   * Print {@code message} to <output-root>/log/debug.log file. This log file will also include 'info' and 'warning' messages.
   */
  fun debug(message: String)

  /**
   * Report an error and stop the build process
   */
  fun error(message: String)

  fun error(message: String, cause: Throwable)

  /**
   * @deprecated use {@link #compilationErrors(java.lang.String, java.util.List)} instead; if compilation errors are reported individually they are shown in separate blocks in TeamCity
   */
  fun compilationError(compilerName: String, message: String)

  fun compilationErrors(compilerName: String, messages: List<String>)

  fun progress(message: String)

  fun buildStatus(message: String)

  fun setParameter(parameterName: String, value: String)

  fun <V> block(blockName: String, task: () -> V): V

  fun artifactBuilt(relativeArtifactPath: String)

  fun reportStatisticValue(key: String, value: String)

  fun forkForParallelTask(taskName: String): BuildMessages

  /**
   * Must be invoked from the main thread when all forks have been finished
   */
  fun onAllForksFinished()

  /**
   * Must be invoked for the forked instance on the thread where it is executing before the task is started.
   * It's required to correctly handle messages from Ant tasks.
   */
  fun onForkStarted()

  /**
   * Must be invoked for the forked instance on the thread where it is executing when the task is finished
   */
  fun onForkFinished()
}
