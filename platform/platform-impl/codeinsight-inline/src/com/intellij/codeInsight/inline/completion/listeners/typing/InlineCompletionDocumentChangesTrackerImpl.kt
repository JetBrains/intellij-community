// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.listeners.typing

import com.intellij.codeInsight.inline.completion.InlineCompletionEvent
import com.intellij.codeInsight.inline.completion.TypingEvent
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationListener
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.util.application
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.jetbrains.annotations.ApiStatus
import kotlin.reflect.KMutableProperty0

internal sealed interface InlineCompletionDocumentChangesTracker {
  val ignoreCaretMovements: Boolean

  val ignoreDocumentChanges: Boolean

  /**
   * Informs this tracker that the next [DocumentEvent] that changes the same as [typingEvent] will create an event, that will be
   * handled by [InlineCompletionHandler].
   *
   * Note that [allowTyping] and [onDocumentEvent] must be called within the same Write Action,
   * otherwise [allowTyping] will not influence on the next [onDocumentEvent].
   *
   * @see onDocumentEvent
   */
  fun allowTyping(typingEvent: TypingEvent)

  /**
   * If [documentEvent] offers the same as the last [allowTyping], then it creates [InlineCompletionEvent.DocumentChange] and invokes it.
   * Otherwise, [documentEvent] is considered as 'non-typing' and a current session is invalidated.
   * No new session is started in such a case.
   *
   * @see allowTyping
   * @see InlineCompletionDocumentChangesTrackerImpl.getDocumentChangeEvent
   */
  fun onDocumentEvent(documentEvent: DocumentEvent, editor: Editor)
}

internal class InlineCompletionDocumentChangesTrackerImpl(
  parentDisposable: Disposable,
  private val sendEvent: (InlineCompletionEvent) -> Unit,
  private val invalidateOnUnknownChange: () -> Unit,
) : InlineCompletionDocumentChangesTracker {

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

  override var ignoreCaretMovements: Boolean = false
    set(value) {
      ThreadingAssertions.assertEventDispatchThread()
      field = value
    }

  override var ignoreDocumentChanges: Boolean = false
    set(value) {
      ThreadingAssertions.assertEventDispatchThread()
      field = value
    }

  @RequiresEdt
  override fun allowTyping(typingEvent: TypingEvent) {
    ThreadingAssertions.assertEventDispatchThread()
    lastTypingEvent = typingEvent
  }

  @RequiresEdt
  override fun onDocumentEvent(documentEvent: DocumentEvent, editor: Editor) {
    ThreadingAssertions.assertEventDispatchThread()
    val event = getDocumentChangeEvent(documentEvent, editor)
    if (event != null) sendEvent(event) else {
      if (!ignoreDocumentChanges) {
        invalidateOnUnknownChange()
      }
    }
  }

  @RequiresEdt
  fun <T> withIgnoringDocumentChanges(block: () -> T): T = withIgnoringChanges(
    property = ::ignoreDocumentChanges,
    block = block,
    getLazyErrorMessage = { "The state of disabling document changes tracker is switched outside." }
  )

  @ApiStatus.Experimental
  @RequiresEdt
  internal fun <T> withIgnoringCaretMovement(block: () -> T): T = withIgnoringChanges(
    property = ::ignoreCaretMovements,
    block = block,
    getLazyErrorMessage = { "The state of disabling caret movements tracker is switched outside." }
  )

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
  private fun getDocumentChangeEvent(documentEvent: DocumentEvent, editor: Editor): InlineCompletionEvent.DocumentChange? {
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

  private inline fun <T> withIgnoringChanges(
    property: KMutableProperty0<Boolean>,
    block: () -> T,
    getLazyErrorMessage: () -> String
  ): T {
    ThreadingAssertions.assertEventDispatchThread()
    val currentIgnoreChanges = property.get()
    property.set(true)
    return try {
      block()
    }
    finally {
      check(property.get(), getLazyErrorMessage)
      property.set(currentIgnoreChanges)
    }
  }
}
