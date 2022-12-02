// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.progress.impl

import com.intellij.openapi.progress.ProgressReporter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal class IndeterminateTextDetailsProgressReporter(parentScope: CoroutineScope) : BaseProgressReporter(parentScope) {

  private val childrenHandler: ChildrenHandler<TextDetails> = ChildrenHandler(cs, TextDetails.NULL, ::reduceTextDetails)

  val progressUpdates: Flow<TextDetails>
    get() = childrenHandler.progressUpdates.map {
      it.state
    }

  override fun createStep(duration: Double?, text: ProgressText?): ProgressReporter {
    if (text == null) {
      val reporter = IndeterminateTextDetailsProgressReporter(cs)
      childrenHandler.applyChildUpdates(reporter, reporter.progressUpdates)
      return reporter
    }
    else {
      val reporter = IndeterminateTextProgressReporter(cs)
      val childUpdates = reporter.progressUpdates.map { childText ->
        TextDetails(text = text, details = childText)
      }
      childrenHandler.applyChildUpdates(reporter, childUpdates)
      return reporter
    }
  }
}
