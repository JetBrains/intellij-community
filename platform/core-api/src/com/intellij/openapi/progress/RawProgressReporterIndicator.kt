// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.progress

import com.intellij.openapi.application.ModalityState
import com.intellij.platform.util.progress.RawProgressReporter

internal class RawProgressReporterIndicator(
  private val reporter: RawProgressReporter,
  contextModality: ModalityState,
) : EmptyProgressIndicator(contextModality) {

  override fun setText(text: String?) {
    reporter.text(text)
  }

  override fun setText2(text: String?) {
    reporter.details(text)
  }

  override fun setFraction(fraction: Double) {
    reporter.fraction(fraction)
  }

  override fun setIndeterminate(indeterminate: Boolean) {
    if (indeterminate) {
      reporter.fraction(null)
    }
    else {
      reporter.fraction(0.0)
    }
  }
}
