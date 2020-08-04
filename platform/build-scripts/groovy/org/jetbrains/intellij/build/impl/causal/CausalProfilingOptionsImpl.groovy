// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.impl.causal


import org.jetbrains.intellij.build.causal.CausalProfilingOptions
import org.jetbrains.intellij.build.causal.ProgressPoint

class CausalProfilingOptionsImpl extends CausalProfilingOptions {

  @Override
  String getTestClass() {
    return "com.intellij.causal.HighlightHugeCallChainCausalTest"
  }

  @Override
  ProgressPoint getProgressPoint() {
    return new ProgressPoint("com.intellij.codeInsight.daemon.DaemonAnalyzerTestCase", 306)
  }

  @Override
  String getSearchScope() {
    return "com.intellij"
  }

  @Override
  Collection<String> getScopesToIgnore() {
    return [
      "com.intellij.openapi.application",
      "com.intellij.openapi.progress",
      "com.intellij.testFramework",
      "com.intellij.junit4",
      "com.intellij.rt",
      "com.intellij.ide.IdeEventQueue",
      "com.intellij.util.concurrency.AppDelayQueue",
      "com.intellij.util.TimeoutUtil",
      "com.intellij.java.codeInsight.daemon.impl.DaemonRespondToChangesPerformanceTest"
    ]
  }
}
