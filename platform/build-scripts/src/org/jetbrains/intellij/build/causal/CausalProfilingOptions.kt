// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.causal

internal class CausalProfilingOptions(
  /**
   * Fqn of test class that must be run with causal profiler
   */
  val testClass: String,
  val progressPoint: ProgressPoint,
  /**
   * Such prefix that if class fqn has it, then this class can be selected for experiment
   */
  val searchScope: String,
  private val additionalSearchScopes: List<String> = emptyList(),
  /**
   * Such prefixes that if class fqn has at least one of them, then this class will never be selected for experiment.
   * Note that any string returned by this method should have string returned by {@link #searchScope},
   * or any string returned by {@link #additionalSearchScopes}, as a prefix.
   */
  val scopesToIgnore: List<String> = emptyList(),
) {
  companion object {
    val IMPL = CausalProfilingOptions(
      testClass = "com.intellij.causal.HighlightHugeCallChainCausalTest",
      progressPoint = ProgressPoint("com.intellij.codeInsight.daemon.DaemonAnalyzerTestCase", 306),
      searchScope = "com.intellij",
      scopesToIgnore = listOf(
        "com.intellij.causal",
        "com.intellij.TestAll",
        "com.intellij.concurrency",
        "com.intellij.codeInsight.daemon.impl",
        "com.intellij.openapi.application",
        "com.intellij.openapi.progress",
        "com.intellij.testFramework",
        "com.intellij.junit4",
        "com.intellij.rt",
        "com.intellij.ide.IdeEventQueue",
        "com.intellij.util.TimeoutUtil",
        "com.intellij.java.codeInsight.daemon.impl.DaemonRespondToChangesPerformanceTest"
      )
    )
  }

  fun buildAgentArgsString(): String {
    var agentArgs = "progress-point=${progressPoint.classFqn}:${progressPoint.lineNumber}_search=${searchScope}"
    val jointAdditionalSearchScopes = additionalSearchScopes.joinToString(separator = "|")
    if (!jointAdditionalSearchScopes.isEmpty()) {
      agentArgs += "|${jointAdditionalSearchScopes}"
    }

    val jointScopesToIgnore = scopesToIgnore.joinToString(separator = "|")
    if (!jointScopesToIgnore.isEmpty()) {
      agentArgs += "_ignore=${jointScopesToIgnore}"
    }
    return agentArgs
  }
}

data class ProgressPoint(
  val classFqn: String,
  val lineNumber: Int,
)
