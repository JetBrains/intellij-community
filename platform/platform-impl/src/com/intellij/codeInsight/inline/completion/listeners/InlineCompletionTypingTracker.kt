// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.listeners

import com.intellij.codeInsight.inline.completion.InlineCompletionEvent
import com.intellij.codeInsight.inline.completion.TypingEvent
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationListener
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.util.application
import com.intellij.util.concurrency.annotations.RequiresEdt


internal class InlineCompletionTypingTracker(parentDisposable: Disposable) {

  private var lastTypingEvent: TypingEvent? = null

  init {
    application.addApplicationListener(
      object : ApplicationListener {
        override fun afterWriteActionFinished(action: Any) {
          lastTypingEvent = null
        }
      },
      parentDisposable
    )
  }

  /**
   * Informs this tracker that the next [DocumentEvent] that changes the same as [typingEvent] will create an event, that will be
   * handled by [InlineCompletionHandler].
   *
   * Note that [allowTyping] and [getDocumentChangeEvent] must be called within the same Write Action, otherwise [allowTyping]
   * will not influence on the next [getDocumentChangeEvent].
   *
   * @see getDocumentChangeEvent
   */
  @RequiresEdt
  fun allowTyping(typingEvent: TypingEvent) {
    lastTypingEvent = typingEvent
  }

  /**
   * If [documentEvent] was confirmed by the previous [allowTyping], which means they change the same in a document, this method
   * returns [InlineCompletionEvent.DocumentChange] that will be handled by [InlineCompletionHandler]. Otherwise, [documentEvent]
   * will cause [InlineCompletionHandler] to invalidate the current session (without starting a new one).
   *
   * Note that [allowTyping] and [getDocumentChangeEvent] must be called within the same Write Action, otherwise [allowTyping]
   * will not influence on the next [getDocumentChangeEvent].
   *
   * @see allowTyping
   */
  @RequiresEdt
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
