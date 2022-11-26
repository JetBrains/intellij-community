// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import com.intellij.openapi.util.ThrowableComputable

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

  fun compilationErrors(compilerName: String, messages: List<String>)

  fun progress(message: String)

  fun buildStatus(message: String)

  fun setParameter(parameterName: String, value: String)

  fun <V> block(blockName: String, task: ThrowableComputable<V, Exception>): V

  fun artifactBuilt(relativeArtifactPath: String)

  fun reportStatisticValue(key: String, value: String)
}
