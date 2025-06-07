// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.listeners

import com.intellij.codeInsight.inline.completion.InlineCompletion
import com.intellij.codeInsight.inline.completion.logs.InlineCompletionUsageTracker.ShownEvents.FinishType
import com.intellij.codeInsight.inline.completion.session.InlineCompletionSession
import com.intellij.openapi.editor.event.SelectionEvent
import com.intellij.openapi.editor.event.SelectionListener

internal class InlineCompletionSelectionListener : SelectionListener {

  override fun selectionChanged(e: SelectionEvent) {
    if (e.newRange.isEmpty) {
      return
    }
    val handler = InlineCompletion.getHandlerOrNull(e.editor) ?: return
    val session = InlineCompletionSession.getOrNull(e.editor)?.takeIf { !it.context.isDisposed } ?: return
    // LLM-16846
    handler.hide(session.context, FinishType.CARET_CHANGED) // Not a very accurate reason, but it's the most accurate one.
  }
}
