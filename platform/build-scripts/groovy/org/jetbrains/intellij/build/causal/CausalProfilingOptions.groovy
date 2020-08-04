// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.causal


import org.jetbrains.intellij.build.impl.causal.CausalProfilingOptionsImpl

abstract class CausalProfilingOptions {

  static final CausalProfilingOptions IMPL = new CausalProfilingOptionsImpl()

  /**
   * @return fqn of test class that must be run with causal profiler
   */
  abstract String getTestClass()

  protected abstract ProgressPoint getProgressPoint()

  /**
   * @return such prefix that if class fqn has it, then this class can be selected for experiment
   */
  protected abstract String getSearchScope()

  /**
   * @return such prefixes that if class fqn has at least one of them, then this class will never be selected for experiment.
   * Note that any string returned by this method should have string returned by {@link #getSearchScope} as a prefix.
   */
  protected abstract Collection<String> getScopesToIgnore()

  String buildAgentArgsString() {
    def agentArgs = "pkg=${searchScope}_progress-point=${progressPoint.classFqn}:${progressPoint.lineNumber}"
    def jointScopesToIgnore = scopesToIgnore.join("|")
    if (!jointScopesToIgnore.isEmpty()) {
      return agentArgs << "_ignore=${jointScopesToIgnore}"
    }
    return agentArgs
  }
}
