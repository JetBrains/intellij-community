// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.logs

import com.intellij.codeInsight.inline.completion.InlineCompletionEventType
import com.intellij.codeInsight.inline.completion.InlineCompletionEventAdapter
import com.intellij.codeInsight.inline.completion.logs.InlineCompletionUsageTracker.ShownEvents.FinishType
import com.intellij.codeInsight.inline.completion.logs.statistics.UserFactorDescriptions
import com.intellij.codeInsight.inline.completion.logs.statistics.UserFactorStorage

private val EXPLICIT_CANCEL_TYPES = setOf(
  FinishType.MOUSE_PRESSED,
  FinishType.CARET_CHANGED,
  FinishType.ESCAPE_PRESSED
)

private val SELECTED_TYPE = FinishType.SELECTED

private val INVALIDATED_TYPE = FinishType.INVALIDATED

internal class UserFactorsListener() : InlineCompletionEventAdapter {
  /**
   * This field is not thread-safe, please access it only on EDT.
   */
  private var holder = Holder()

  /**
   * Fields inside [Holder] are not thread-safe, please access them only on EDT.
   */
  private class Holder() {
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
    if(holder.wasShown && event.finishType != SELECTED_TYPE) {
      UserFactorStorage.apply( UserFactorDescriptions.ACCEPTANCE_RATE_FACTORS) {
        it.fireLookupElementShowUp()
      }
    }
    if(holder.wasShown) {
      when (event.finishType) {
        in EXPLICIT_CANCEL_TYPES -> {
          UserFactorStorage.apply( UserFactorDescriptions.COMPLETION_FINISH_TYPE) {
            it.fireExplicitCancel()
          }
        }
        SELECTED_TYPE -> {
          UserFactorStorage.apply( UserFactorDescriptions.ACCEPTANCE_RATE_FACTORS) {
            it.fireLookupElementSelected()
          }
          UserFactorStorage.apply( UserFactorDescriptions.COMPLETION_FINISH_TYPE) {
            it.fireSelected()
          }
          UserFactorStorage.apply(UserFactorDescriptions.PREFIX_LENGTH_ON_COMPLETION) {
            it.fireCompletionPerformed(holder.prefixLength)
          }
        }
        INVALIDATED_TYPE -> {
          UserFactorStorage.apply( UserFactorDescriptions.COMPLETION_FINISH_TYPE) {
            it.fireInvalidated()
          }
        }
        else -> {
          UserFactorStorage.apply( UserFactorDescriptions.COMPLETION_FINISH_TYPE) {
            it.fireOther()
          }
        }
      }
    }
  }

}