// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.listeners.typing

import com.intellij.codeInsight.inline.completion.InlineCompletionEvent
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.editor.event.CaretListener
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.removeUserData
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.jetbrains.annotations.ApiStatus
import kotlin.reflect.KMutableProperty0

/**
 * Manages the lifecycle of a "typing session" for inline completion.
 *
 * This class acts as a state machine to track sequences of user typing events.
 *
 * It works by attaching a [InlineCompletionTypingSession] to an [Editor] instance and transitioning it
 * through various [InlineCompletionTypingStage] states in response to editor events. All public methods
 * require execution on the Event Dispatch Thread (EDT) for thread-safe editor access.
 *
 * @see InlineCompletionTypingStage
 * @see InlineCompletionTypingSession
 * @see InlineCompletionTypingSessionCaretListener
 */
@ApiStatus.Internal
class InlineCompletionTypingSessionTracker(
  private val sendEvent: (InlineCompletionEvent) -> Unit,
  private val invalidateOnUnknownChange: () -> Unit,
) {

  var ignoreCaretMovements: Boolean = false
    set(value) {
      ThreadingAssertions.assertEventDispatchThread()
      field = value
    }

  var ignoreDocumentChanges: Boolean = false
    set(value) {
      ThreadingAssertions.assertEventDispatchThread()
      field = value
    }

  /**
   * Starts a new typing session for the given editor.
   * A [InlineCompletionTypingSession] object is created and stored in the editor's user data.
   */
  @RequiresEdt
  fun startTypingSession(editor: Editor) {
    ThreadingAssertions.assertEventDispatchThread()
    val context = InlineCompletionTypingSession.InlineCompletionTypingSessionStageContext(sendEvent) {
      invalidateOnUnknownChange()
      endTypingSession(editor)
    }
    editor.putUserData(TYPING_SESSION_KEY, InlineCompletionTypingSession(context))
  }

  /**
   * Forwards a document change event to the active typing session, if one exists.
   */
  @RequiresEdt
  fun collectTypedCharOrInvalidateSession(documentEvent: DocumentEvent, editor: Editor) {
    ThreadingAssertions.assertEventDispatchThread()
    val session = editor.getUserData(TYPING_SESSION_KEY)
    if (session == null) {
      if (!ignoreDocumentChanges) {
        invalidateOnUnknownChange()
      }
      return
    }
    session.handleDocumentChange(documentEvent, editor)
  }

  /**
   * Notifies the active session that the next document change is expected to be
   * the insertion of a paired enclosure character (e.g., `)` or `}`).
   */
  @RequiresEdt
  fun expectPairedEnclosure(editor: Editor, excpectedEnclosure: String) {
    ThreadingAssertions.assertEventDispatchThread()
    editor.getUserData(TYPING_SESSION_KEY)?.handlePairedEnclosure(excpectedEnclosure)
  }

  /**
   * Terminates the typing session for the editor.
   *
   * If the session was in a state other than the initial one ([AwaitInitialEvent]),
   * it implies an unfinished or unexpected sequence of events, so the
   * [com.intellij.codeInsight.inline.completion.session.InlineCompletionSession] is invalidated.
   */
  @RequiresEdt
  fun endTypingSession(editor: Editor) {
    ThreadingAssertions.assertEventDispatchThread()
    val session = editor.getUserData(TYPING_SESSION_KEY) ?: return

    if (session.stage != InlineCompletionTypingStage.AwaitInitialEvent) {
      invalidateOnUnknownChange()
    }

    editor.removeUserData(TYPING_SESSION_KEY)
  }

  @RequiresEdt
  fun isAlive(editor: Editor): Boolean {
    ThreadingAssertions.assertEventDispatchThread()
    return editor.getUserData(TYPING_SESSION_KEY) != null
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

  private inline fun <T> withIgnoringChanges(
    property: KMutableProperty0<Boolean>,
    block: () -> T,
    getLazyErrorMessage: () -> String,
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

  /**
   * A class representing an active session. It holds the current state
   * of the state machine and delegates incoming events to it.
   */
  internal class InlineCompletionTypingSession(private val typingContext: InlineCompletionTypingSessionStageContext) {
    var stage: InlineCompletionTypingStage = InlineCompletionTypingStage.AwaitInitialEvent

    fun handleDocumentChange(event: DocumentEvent, editor: Editor) {
      stage = stage.onDocumentChange(typingContext, event, editor)
    }

    fun handlePairedEnclosure(expectedEnclosure: String) {
      stage = stage.onPairedEnclosure(typingContext, expectedEnclosure)
    }

    fun handleCaretMove(event: CaretEvent, editor: Editor) {
      stage = stage.onCaretMove(typingContext, event, editor)
    }

    /**
     * A context for the typing session state machine.
     * It contains the necessary logic for sending events and invalidating the session.
     * [InlineCompletionTypingSessionStageContext.invalidateOnUnknownChange] may end the [com.intellij.codeInsight.inline.completion.session.InlineCompletionSession]
     * and invalidate the [com.intellij.codeInsight.inline.completion.session.InlineCompletionSession]
     */
    data class InlineCompletionTypingSessionStageContext(
      val sendEvent: (InlineCompletionEvent) -> Unit,
      val invalidateOnUnknownChange: () -> Unit,
    )
  }

  /**
   * A global caret listener that forwards caret changes to the active [InlineCompletionTypingSession]
   * for the corresponding editor.
   */
  internal class InlineCompletionTypingSessionCaretListener : CaretListener {
    override fun caretPositionChanged(event: CaretEvent) {
      event.editor.getUserData(TYPING_SESSION_KEY)?.handleCaretMove(event, event.editor)
    }
  }

  companion object {
    private val TYPING_SESSION_KEY = Key.create<InlineCompletionTypingSession>("inline.completion.typing.session.typingContext")
  }
}