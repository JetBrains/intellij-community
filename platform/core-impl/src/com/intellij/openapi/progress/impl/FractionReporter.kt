// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.progress.impl

import com.intellij.openapi.progress.ProgressReporter
import com.intellij.openapi.progress.RawProgressReporter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal class FractionReporter(parentScope: CoroutineScope) : BaseProgressReporter(parentScope) {

  private val childrenHandler: ChildrenHandler<Nothing?> = ChildrenHandler(cs, null) { null }

  val progressUpdates: Flow<Double>
    get() = childrenHandler.progressUpdates.map {
      it.fraction
    }

  override fun createStep(duration: Double?, text: ProgressText?): ProgressReporter {
    if (duration == null) {
      return SilentProgressReporter(cs)
    }
    else {
      val step = FractionReporter(cs)
      childrenHandler.applyChildUpdates(step, duration, step.childrenHandler.progressUpdates)
      return step
    }
  }

  override fun asRawReporter(): RawProgressReporter = object : RawProgressReporter {

    override fun fraction(fraction: Double?) {
      check(fraction == null || fraction in .0..1.0)
      childrenHandler.progressState.value = FractionState(fraction ?: -1.0, null)
    }
  }
}
