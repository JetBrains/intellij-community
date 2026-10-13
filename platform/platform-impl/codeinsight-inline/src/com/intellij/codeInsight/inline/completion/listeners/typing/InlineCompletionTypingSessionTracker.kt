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
 * It works by attaching a [TypingSession] to an [Editor] instance and transitioning it
 * through various [InlineCompletionTypingState] states in response to editor events. All public methods
 * require execution on the Event Dispatch Thread (EDT) for thread-safe editor access.
 *
 * @see InlineCompletionTypingState
 * @see TypingSession
 * @see TypingSessionCaretListener
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

  @RequiresEdt
  fun startTypingSession(editor: Editor) {
    ThreadingAssertions.assertEventDispatchThread()
    val context = TypingSession.TypingSessionStateContext(sendEvent) {
      invalidateOnUnknownChange()
      endTypingSession(editor)
    }
    editor.putUserData(TYPING_SESSION_KEY, TypingSession(context))
  }


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
   * In the case of a paired enclosure inserted, it will be processed differently than just a regular typed char.
   */
  @RequiresEdt
  fun expectPairedEnclosure(editor: Editor, expectedEnclosure: String) {
    ThreadingAssertions.assertEventDispatchThread()
    editor.getUserData(TYPING_SESSION_KEY)?.handlePairedEnclosure(expectedEnclosure)
  }

  @RequiresEdt
  fun endTypingSession(editor: Editor) {
    ThreadingAssertions.assertEventDispatchThread()
    val session = editor.getUserData(TYPING_SESSION_KEY) ?: return

    if (session.state != InlineCompletionTypingState.AwaitInitialEvent) {
      invalidateOnUnknownChange()
    }

    editor.removeUserData(TYPING_SESSION_KEY)
  }

  @RequiresEdt
  fun isTypingInProgress(editor: Editor): Boolean {
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
   * Holds the current state of the state machine and delegates incoming events to it.
   *
   * Typing session lasts from start handling user typing until the editor
   * completes all follow-ups (auto-pairs, spaces, caret moves,
   * additional symbol insertion e.g., paredEnclosure or space after colon in JSON).
   */
  internal class TypingSession(private val typingContext: TypingSessionStateContext) {
    var state: InlineCompletionTypingState = InlineCompletionTypingState.AwaitInitialEvent

    @RequiresEdt
    fun handleDocumentChange(event: DocumentEvent, editor: Editor) {
      state = state.onDocumentChange(typingContext, event, editor)
    }

    @RequiresEdt
    fun handlePairedEnclosure(expectedEnclosure: String) {
      state = state.onPairedEnclosure(typingContext, expectedEnclosure)
    }

    @RequiresEdt
    fun handleCaretMove(event: CaretEvent, editor: Editor) {
      state = state.onCaretMove(typingContext, event, editor)
    }

    /**
     * Used for passing the necessary function to the state machine.
     */
    internal data class TypingSessionStateContext(
      val sendEvent: (InlineCompletionEvent) -> Unit,
      val invalidateOnUnknownChange: () -> Unit,
    )
  }

  /**
   * A global caret listener that forwards caret events to the active [TypingSession]
   * to check if caret movement during [TypingSession] is expected.
   */
  internal class TypingSessionCaretListener : CaretListener {
    override fun caretPositionChanged(event: CaretEvent) {
      event.editor.getUserData(TYPING_SESSION_KEY)?.handleCaretMove(event, event.editor)
    }
  }

  companion object {
    private val TYPING_SESSION_KEY = Key.create<TypingSession>("inline.completion.typing.session.typingContext")
  }
}