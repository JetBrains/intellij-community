// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.debounce

import com.intellij.codeInsight.inline.completion.InlineCompletionEventAdapter
import com.intellij.codeInsight.inline.completion.InlineCompletionEventType
import com.intellij.codeInsight.inline.completion.logs.InlineCompletionUsageTracker.ShownEvents.FinishType
import com.intellij.codeInsight.inline.completion.session.InlineCompletionSession
import com.intellij.openapi.editor.Editor

/**
 * Listener that observes inline completion lifecycle and records
 * accept/reject finish type into [InlineCompletionFinishedCompletionsStorage] per provider.
 */
internal class InlineCompletionAdaptiveDebounceListener(private val editor: Editor) : InlineCompletionEventAdapter {

  private fun serviceOrNull(): InlineCompletionFinishedCompletionsStorage? = editor.project?.let { InlineCompletionFinishedCompletionsStorage.getInstance(it) }

  override fun onHide(event: InlineCompletionEventType.Hide) {
    val result = when (event.finishType) {
      FinishType.CARET_CHANGED,
      FinishType.EDITOR_REMOVED,
      FinishType.FOCUS_LOST,
      FinishType.TYPED,
      FinishType.EMPTY,
      FinishType.ERROR,
      FinishType.OTHER,
      FinishType.BACKSPACE_PRESSED,
      FinishType.KEY_PRESSED,
      FinishType.INVALIDATED,
      FinishType.MOUSE_PRESSED,
      FinishType.DOCUMENT_CHANGED,
        -> InlineCompletionFinishedCompletionsStorage.Result.OTHER
      FinishType.ESCAPE_PRESSED -> InlineCompletionFinishedCompletionsStorage.Result.REJECTED
      FinishType.SELECTED -> InlineCompletionFinishedCompletionsStorage.Result.ACCEPTED
    }
    val provider = InlineCompletionSession.getOrNull(editor)?.provider?.id
    provider?.let { serviceOrNull()?.record(it, result) }
  }
}
