// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.target

import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.util.Key

class TargetProgressIndicatorAdapter(private val progress: ProgressIndicator) : TargetProgressIndicator {
  override fun addText(text: String, key: Key<*>) {
    progress.text2 = text
  }

  override fun isCanceled(): Boolean {
    return progress.isCanceled
  }

  override fun stop() {
    progress.stop()
  }

  override fun isStopped(): Boolean {
    // progress has shorter lifetime than target
    return false
  }
}