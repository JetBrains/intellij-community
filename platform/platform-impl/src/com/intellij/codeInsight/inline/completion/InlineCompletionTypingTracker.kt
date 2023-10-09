// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.util.concurrency.annotations.RequiresEdt


internal class InlineCompletionTypingTracker {

  private var lastTypingEvent: TypingEvent? = null

  @RequiresEdt
  fun allowTyping(typingEvent: TypingEvent) {
    lastTypingEvent = typingEvent
  }

  @RequiresEdt
  fun getDocumentChangeEvent(documentEvent: DocumentEvent, editor: Editor): InlineCompletionEvent.DocumentChange? {
    val lastTypingEvent = lastTypingEvent
    reset()
    return if (lastTypingEvent != null && lastTypingEvent.matches(documentEvent)) {
      InlineCompletionEvent.DocumentChange(lastTypingEvent, editor)
    }
    else {
      null
    }
  }

  @RequiresEdt
  fun reset() {
    lastTypingEvent = null
  }

  private fun TypingEvent.matches(event: DocumentEvent): Boolean {
    return event.oldLength == 0 && typed == event.newFragment.toString()
  }
}
