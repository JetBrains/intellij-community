// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.progress.impl

import com.intellij.openapi.progress.ProgressReporter
import com.intellij.openapi.progress.RawProgressReporter

internal object EmptyProgressReporter : ProgressReporter {
  override fun step(endFraction: Double, text: ProgressText?): ProgressReporter = EmptyProgressReporter
  override fun durationStep(duration: Double, text: ProgressText?): ProgressReporter = EmptyProgressReporter
  override fun close(): Unit = Unit
  override fun rawReporter(): RawProgressReporter = EmptyRawProgressReporter
}
