// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl

import com.intellij.codeHighlighting.EditorBoundHighlightingPass
import com.intellij.psi.impl.source.resolve.reference.ReferenceProvidersRegistry
import com.intellij.util.PerformanceAssertions

internal class PerformanceAssertionsImpl: PerformanceAssertions() {
  override fun checkDoesNotAffectHighlighting() {
    GeneralHighlightingPass.assertHighlightingPassNotRunning()
    ReferenceProvidersRegistry.assertNotContributingReferences()
    EditorBoundHighlightingPass.assertHighlightingPassNotRunning()
  }
}