// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.progress.impl

import com.intellij.openapi.progress.ProgressReporter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

internal class TextProgressReporter(parentScope: CoroutineScope) : BaseProgressReporter(parentScope) {

  private val childrenHandler: ChildrenHandler<ProgressText?> = ChildrenHandler(cs, null, ::reduceText)

  val progressUpdates: Flow<FractionState<ProgressText?>> = childrenHandler.progressUpdates

  override fun createStep(duration: Double?, text: ProgressText?): ProgressReporter {
    when {
      text == null && duration == null -> {
        val step = IndeterminateTextProgressReporter(cs)
        childrenHandler.applyChildUpdates(step, step.progressUpdates)
        return step
      }
      text == null && duration != null -> {
        val step = TextProgressReporter(cs)
        childrenHandler.applyChildUpdates(step, duration, step.progressUpdates)
        return step
      }
      text != null && duration == null -> {
        val step = SilentProgressReporter(cs)
        childrenHandler.applyChildUpdates(step, flowOf(text))
        return step
      }
      text != null && duration != null -> {
        val step = FractionReporter(cs)
        childrenHandler.applyChildUpdates(step, duration, step.progressUpdates.map { childFraction ->
          FractionState(fraction = childFraction, text)
        })
        return step
      }
      else -> error("keeping compiler happy")
    }
  }
}
