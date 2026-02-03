// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.onboarding

import com.intellij.codeInsight.inline.completion.InlineCompletionEventAdapter
import com.intellij.codeInsight.inline.completion.InlineCompletionEventType
import com.intellij.codeInsight.inline.completion.session.InlineCompletionSession
import com.intellij.codeInsight.inline.completion.tooltip.InlineCompletionTooltip
import com.intellij.codeInsight.inline.completion.tooltip.onboarding.InlineCompletionOnboardingComponent
import com.intellij.openapi.editor.Editor

internal class InlineCompletionOnboardingListener private constructor(private val editor: Editor) : InlineCompletionEventAdapter {
  private var state: State? = null

  override fun onRequest(event: InlineCompletionEventType.Request) {
    state = State()
  }

  override fun onEmpty(event: InlineCompletionEventType.Empty) {
    state?.isEmpty = true
  }

  override fun onCompletion(event: InlineCompletionEventType.Completion) {
    if (state?.isEmpty == false && event.cause == null) {
      InlineCompletionSession.getOrNull(editor)?.let { session ->
        if (InlineCompletionOnboardingComponent.getInstance().shouldExplicitlyDisplayTooltip()) {
          InlineCompletionTooltip.show(session)
        }
      }
    }
  }

  private class State(var isEmpty: Boolean = false)

  companion object {
    fun createIfOnboarding(editor: Editor): InlineCompletionOnboardingListener? {
      return if (InlineCompletionOnboardingComponent.getInstance().shouldExplicitlyDisplayTooltip()) {
        InlineCompletionOnboardingListener(editor)
      }
      else null
    }
  }
}
