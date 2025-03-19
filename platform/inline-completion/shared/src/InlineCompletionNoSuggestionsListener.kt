// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion

import com.intellij.codeInsight.hint.HintManager
import com.intellij.codeInsight.inline.completion.logs.InlineCompletionUsageTracker
import com.intellij.codeInsight.inline.completion.session.InlineCompletionSession
import com.intellij.lang.LangBundle
import com.intellij.openapi.editor.Editor

internal class InlineCompletionNoSuggestionsListener(private val editor: Editor) : InlineCompletionEventAdapter {

  override fun onHide(event: InlineCompletionEventType.Hide) {
    if (event.finishType == InlineCompletionUsageTracker.ShownEvents.FinishType.EMPTY) {
      val session = InlineCompletionSession.getOrNull(editor)
      if (session != null && session.request.event is InlineCompletionEvent.DirectCall) {
        HintManager.getInstance().showInformationHint(editor, LangBundle.message("completion.no.suggestions"), HintManager.ABOVE)
      }
    }
  }
}
