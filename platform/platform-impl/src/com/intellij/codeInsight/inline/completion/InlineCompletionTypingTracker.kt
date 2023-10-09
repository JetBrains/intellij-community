// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationListener
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.util.application
import com.intellij.util.concurrency.annotations.RequiresBlockingContext
import com.intellij.util.concurrency.annotations.RequiresEdt


internal class InlineCompletionTypingTracker(parentDisposable: Disposable) {

  private var lastTypingEvent: TypingEvent? = null

  init {
    // [allowTyping] must be called in the same Write Action as [getDocumentChangeEvent]
    application.addApplicationListener(
      object : ApplicationListener {
        override fun afterWriteActionFinished(action: Any) {
          lastTypingEvent = null
        }
      },
      parentDisposable
    )
  }

  @RequiresEdt
  @RequiresBlockingContext
  fun allowTyping(typingEvent: TypingEvent) {
    lastTypingEvent = typingEvent
  }

  @RequiresEdt
  @RequiresBlockingContext
  fun getDocumentChangeEvent(documentEvent: DocumentEvent, editor: Editor): InlineCompletionEvent.DocumentChange? {
    val lastTypingEvent = lastTypingEvent
    this.lastTypingEvent = null
    return if (lastTypingEvent != null && lastTypingEvent.matches(documentEvent)) {
      InlineCompletionEvent.DocumentChange(lastTypingEvent, editor)
    }
    else {
      null
    }
  }

  private fun TypingEvent.matches(event: DocumentEvent): Boolean {
    return event.oldLength == 0 && typed == event.newFragment.toString()
  }
}
