// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.logs

import com.intellij.codeInsight.inline.completion.InlineCompletionEventType
import com.intellij.codeInsight.inline.completion.InlineCompletionEventAdapter
import com.intellij.codeInsight.inline.completion.logs.InlineCompletionUsageTracker.ShownEvents.FinishType
import com.intellij.codeInsight.inline.completion.logs.statistics.AcceptanceRateFactorsComponent
import com.intellij.codeInsight.inline.completion.logs.statistics.CompletionFinishedTypeComponent
import com.intellij.codeInsight.inline.completion.logs.statistics.PrefixLengthComponent

private val EXPLICIT_CANCEL_TYPES = setOf(
  FinishType.MOUSE_PRESSED,
  FinishType.CARET_CHANGED,
  FinishType.ESCAPE_PRESSED
)

internal class UserFactorsListener : InlineCompletionEventAdapter {
  /**
   * This field is not thread-safe, please access it only on EDT.
   */
  private var holder = Holder()

  private val acceptanceRateFactors
    get() = AcceptanceRateFactorsComponent.getInstance()
  private val completionFinishType
    get() = CompletionFinishedTypeComponent.getInstance()
  private val prefixLength
    get() = PrefixLengthComponent.getInstance()

  /**
   * Fields inside [Holder] are not thread-safe, please access them only on EDT.
   */
  private class Holder {
    var wasShown: Boolean = false
    var prefixLength: Int = 0
  }

  override fun onRequest(event: InlineCompletionEventType.Request) {
    holder = Holder()
    val element = if (event.request.startOffset == 0) null else event.request.file.findElementAt(event.request.startOffset - 1)
    holder.prefixLength = if (element != null) (event.request.startOffset - element.textOffset) else 0
  }

  override fun onShow(event: InlineCompletionEventType.Show) {
    holder.wasShown = true
  }

  override fun onHide(event: InlineCompletionEventType.Hide) {
    if (!holder.wasShown) return

    if (event.finishType != FinishType.SELECTED) {
      acceptanceRateFactors.fireElementShowUp()
    }

    when (event.finishType) {
      in EXPLICIT_CANCEL_TYPES -> {
        completionFinishType.fireExplicitCancel()
      }
      FinishType.SELECTED -> {
        acceptanceRateFactors.fireElementSelected()
        completionFinishType.fireSelected()
        prefixLength.fireCompletionPerformed(holder.prefixLength)
      }
      FinishType.INVALIDATED -> {
        completionFinishType.fireInvalidated()
      }
      else -> {
        completionFinishType.fireOther()
      }
    }
  }
}
