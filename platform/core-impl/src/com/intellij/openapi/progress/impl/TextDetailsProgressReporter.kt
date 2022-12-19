// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.progress.impl

import com.intellij.openapi.progress.ProgressReporter
import com.intellij.openapi.progress.RawProgressReporter
import com.intellij.openapi.util.NlsContexts.ProgressDetails
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
class TextDetailsProgressReporter(parentScope: CoroutineScope) : BaseProgressReporter(parentScope) {

  private val childrenHandler: ChildrenHandler<TextDetails> = ChildrenHandler(cs, TextDetails.NULL, ::reduceTextDetails)

  val progressState: Flow<ProgressState> = childrenHandler.progressState.map { (fraction, state) ->
    ProgressState(text = state.text, details = state.details, fraction = fraction)
  }

  override fun createStep(duration: Double?, text: ProgressText?): ProgressReporter {
    when {
      text == null && duration == null -> {
        val step = IndeterminateTextDetailsProgressReporter(cs)
        childrenHandler.applyChildUpdates(step, step.progressUpdates)
        return step
      }
      text == null && duration != null -> {
        val step = TextDetailsProgressReporter(cs)
        childrenHandler.applyChildUpdates(step, duration, step.childrenHandler.progressUpdates)
        return step
      }
      text != null && duration == null -> {
        val step = IndeterminateTextProgressReporter(cs)
        childrenHandler.applyChildUpdates(step, step.progressUpdates.map { childText ->
          TextDetails(text, details = childText)
        })
        return step
      }
      text != null && duration != null -> {
        val step = TextProgressReporter(cs)
        childrenHandler.applyChildUpdates(step, duration, step.progressUpdates.map { (childFraction, childText) ->
          FractionState(childFraction, TextDetails(text, details = childText))
        })
        return step
      }
      else -> error("keeping compiler happy")
    }
  }

  override fun asRawReporter(): RawProgressReporter = object : RawProgressReporter {

    override fun text(text: ProgressText?) {
      childrenHandler.progressState.update { fractionState ->
        fractionState.copy(state = fractionState.state.copy(text = text))
      }
    }

    override fun details(details: @ProgressDetails String?) {
      childrenHandler.progressState.update { fractionState ->
        fractionState.copy(state = fractionState.state.copy(details = details))
      }
    }

    override fun fraction(fraction: Double?) {
      check(fraction == null || fraction in 0.0..1.0)
      childrenHandler.progressState.update { fractionState ->
        fractionState.copy(fraction = fraction ?: -1.0)
      }
    }
  }
}
