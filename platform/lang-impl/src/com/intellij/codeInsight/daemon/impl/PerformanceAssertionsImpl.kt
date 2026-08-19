// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl

import com.intellij.codeInsight.highlighting.PassRunningAssert
import com.intellij.openapi.diagnostic.Logger
import com.intellij.psi.impl.source.resolve.reference.ReferenceProvidersRegistry
import com.intellij.util.PerformanceAssertions

internal class PerformanceAssertionsImpl: PerformanceAssertions() {
  override fun checkDoesNotAffectHighlighting() {
    ReferenceProvidersRegistry.assertNotContributingReferences()
    if (!PerformanceAssertions.isAssertDoesNotAffectHighlightingSuppressed() &&
        !DaemonCodeAnalyzerImpl.assertHighlightingPassNotRunning()) {
      Logger.getInstance(PassRunningAssert::class.java).error("the expensive method should not be called inside the highlighting pass")
    }
  }
}