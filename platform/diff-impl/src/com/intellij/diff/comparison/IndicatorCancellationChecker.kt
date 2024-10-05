// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.comparison

import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator

class IndicatorCancellationChecker(private val myIndicator: ProgressIndicator) : CancellationChecker {
  @Throws(ProcessCanceledException::class)
  override fun checkCanceled() {
    myIndicator.checkCanceled()
  }
}
