/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
   * Report an error and stop the build process
   */
  void error(String message)

  void error(String message, Throwable cause)

  void compilationError(String compilerName, String message)

  void progress(String message)

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
