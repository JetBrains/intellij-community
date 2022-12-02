// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.progress.impl

import com.intellij.openapi.progress.ProgressReporter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

internal class IndeterminateTextProgressReporter(parentScope: CoroutineScope) : BaseProgressReporter(parentScope) {

  private val childrenHandler: ChildrenHandler<ProgressText?> = ChildrenHandler(cs, null, ::reduceText)

  val progressUpdates: Flow<ProgressText?>
    get() = childrenHandler.progressUpdates.map {
      it.state
    }

  override fun createStep(duration: Double?, text: ProgressText?): ProgressReporter {
    if (text == null) {
      val reporter = IndeterminateTextProgressReporter(cs)
      childrenHandler.applyChildUpdates(reporter, reporter.progressUpdates)
      return reporter
    }
    else {
      val reporter = SilentProgressReporter(cs)
      childrenHandler.applyChildUpdates(reporter, flowOf(text))
      return reporter
    }
  }
}
