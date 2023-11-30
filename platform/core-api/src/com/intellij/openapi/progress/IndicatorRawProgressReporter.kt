// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.progress

import com.intellij.openapi.util.NlsContexts.ProgressDetails
import com.intellij.openapi.util.NlsContexts.ProgressText
import com.intellij.platform.util.progress.RawProgressReporter

internal class IndicatorRawProgressReporter(
  private val indicator: ProgressIndicator,
) : RawProgressReporter {

  override fun text(text: @ProgressText String?) {
    indicator.text = text
  }

  override fun details(details: @ProgressDetails String?) {
    indicator.text2 = details
  }

  override fun fraction(fraction: Double?) {
    if (fraction == null) {
      indicator.isIndeterminate = true
    }
    else {
      indicator.isIndeterminate = false
      indicator.fraction = fraction
    }
  }
}
