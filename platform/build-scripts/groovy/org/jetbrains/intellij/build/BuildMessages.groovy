// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build

import groovy.transform.CompileStatic

/**
 * @author nik
 */
@CompileStatic
interface BuildMessages {
  void info(String message)

  void warning(String message)

  /**
   * Print {@code message} to <output-root>/log/debug.log file. This log file will also include 'info' and 'warning' messages.
   */
  void debug(String message)

  /**
   * Report an error and stop the build process
   */
  void error(String message)

  void error(String message, Throwable cause)

  /**
   * @deprecated use {@link #compilationErrors(java.lang.String, java.util.List)} instead; if compilation errors are reported individually they are shown in separate blocks in TeamCity
   */
  void compilationError(String compilerName, String message)

  void compilationErrors(String compilerName, List<String> messages)

  void progress(String message)

  void buildStatus(String message)

  void setParameter(String parameterName, String value)

  def <V> V block(String blockName, Closure<V> body)

  void artifactBuilt(String relativeArtifactPath)

  void reportStatisticValue(String key, String value)

  BuildMessages forkForParallelTask(String taskName)

  /**
   * Must be invoked from the main thread when all forks have been finished
   */
  void onAllForksFinished()

  /**
   * Must be invoked for the forked instance on the thread where it is executing before the task is started.
   * It's required to correctly handle messages from Ant tasks.
   */
  void onForkStarted()

  /**
   * Must be invoked for the forked instance on the thread where it is executing when the task is finished
   */
  void onForkFinished()
}
