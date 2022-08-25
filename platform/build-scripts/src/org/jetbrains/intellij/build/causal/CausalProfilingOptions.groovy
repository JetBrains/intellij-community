// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.causal

import groovy.transform.CompileStatic
import groovy.transform.MapConstructor

@CompileStatic
@MapConstructor
@SuppressWarnings('GrFinalVariableAccess')
class CausalProfilingOptions {

  static final CausalProfilingOptions IMPL = new CausalProfilingOptions(
    testClass: "com.intellij.causal.HighlightHugeCallChainCausalTest",
    progressPoint: new ProgressPoint("com.intellij.codeInsight.daemon.DaemonAnalyzerTestCase", 306),
    searchScope: "com.intellij",
    scopesToIgnore: [
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
    ]
  )

  /**
   * Fqn of test class that must be run with causal profiler
   */
  final String testClass

  final ProgressPoint progressPoint

  /**
   * Such prefix that if class fqn has it, then this class can be selected for experiment
   */
  final String searchScope

  final Collection<String> additionalSearchScopes = []

  /**
   * Such prefixes that if class fqn has at least one of them, then this class will never be selected for experiment.
   * Note that any string returned by this method should have string returned by {@link #searchScope},
   * or any string returned by {@link #additionalSearchScopes}, as a prefix.
   */
  final Collection<String> scopesToIgnore = []

  String buildAgentArgsString() {
    def agentArgs = "progress-point=${progressPoint.classFqn}:${progressPoint.lineNumber}_search=${searchScope}".toString()

    def jointAdditionalSearchScopes = additionalSearchScopes.join("|")
    if (!jointAdditionalSearchScopes.isEmpty()) {
      agentArgs += "|${jointAdditionalSearchScopes}"
    }

    def jointScopesToIgnore = scopesToIgnore.join("|")
    if (!jointScopesToIgnore.isEmpty()) {
      agentArgs += "_ignore=${jointScopesToIgnore}"
    }
    return agentArgs
  }
}
