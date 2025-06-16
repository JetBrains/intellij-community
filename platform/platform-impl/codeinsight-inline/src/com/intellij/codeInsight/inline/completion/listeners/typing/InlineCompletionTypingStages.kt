// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.listeners.typing

import com.intellij.codeInsight.inline.completion.InlineCompletionEvent
import com.intellij.codeInsight.inline.completion.TypingEvent
import com.intellij.codeInsight.inline.completion.listeners.typing.InlineCompletionTypingSessionTracker.InlineCompletionTypingSession
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.editor.event.DocumentEvent

/**
 * Represents a state in the [InlineCompletionTypingSessionTracker] state machine.
 *
 * This is a sealed interface, and its implementations define the specific logic for
 * handling events and transitioning to new states. By default, any unhandled event
 * in a state will transition the machine to an invalid state via [invalidate].
 *
 * @see InlineCompletionTypingSession.InlineCompletionTypingSessionStageContext
 * @see InlineCompletionTypingSessionTracker
 */

internal sealed interface InlineCompletionTypingStage {
  /** Handles the [InlineCompletionTypedHandlerDelegate] signaling that a paired character (e.g., '}') is about to be inserted. */
  fun onPairedEnclosure(ctx: InlineCompletionTypingSession.InlineCompletionTypingSessionStageContext, expectedEnclosure: String): InlineCompletionTypingStage = invalidate(ctx)

  /** Handles a character being typed or text being changed in the document. */
  fun onDocumentChange(ctx: InlineCompletionTypingSession.InlineCompletionTypingSessionStageContext, event: DocumentEvent, editor: Editor): InlineCompletionTypingStage = invalidate(ctx)

  /** Handles the caret moving. */
  fun onCaretMove(ctx: InlineCompletionTypingSession.InlineCompletionTypingSessionStageContext, event: CaretEvent, editor: Editor): InlineCompletionTypingStage = invalidate(ctx)

  /**
   * Centralized function to handle the transition to an invalid state.
   * It calls the invalidation logic from the context and returns the terminal [Invalid] state.
   * This prevents logic duplication and ensures invalidation is always handled correctly.
   */
  fun invalidate(ctx: InlineCompletionTypingSession.InlineCompletionTypingSessionStageContext): InlineCompletionTypingStage {
    ctx.invalidateOnUnknownChange()
    return Invalid
  }

  /**
   * The initial state of the session, waiting for the first event.
   * The first event can be either a paired enclosure or a single symbol, otherwise
   * the [com.intellij.codeInsight.inline.completion.session.InlineCompletionSession] is invalidated,
   * and the [InlineCompletionTypingSessionTracker.InlineCompletionTypingSession] is ended.
   */
  object AwaitInitialEvent : InlineCompletionTypingStage {
    override fun onPairedEnclosure(ctx: InlineCompletionTypingSession.InlineCompletionTypingSessionStageContext, expectedEnclosure: String): InlineCompletionTypingStage = PairedEnclosureReceivedAwaitDocumentEvent(expectedEnclosure)

    override fun onDocumentChange(ctx: InlineCompletionTypingSession.InlineCompletionTypingSessionStageContext, event: DocumentEvent, editor: Editor): InlineCompletionTypingStage {
      if (event.newFragment.length != 1) return invalidate(ctx)
      val symbol = event.newFragment.lastOrNull() ?: return invalidate(ctx)
      val typingEvent = TypingEvent.OneSymbol(symbol, event.offset)
      return AwaitCaretMovement(typingEvent)
    }
  }

  /**
   * A state entered after the [InlineCompletionTypedHandlerDelegate] has signaled a paired enclosure insertion.
   * After waiting for the [onDocumentChange] that inserts the character.
   * In that case, we know that the enclosure was inserted, and we can send [InlineCompletionEvent.DocumentChange],
   * but if the caret will be moved, [invalidate] will be called, because [AwaitInitialEvent] doesn't handle caret movements.
   */
  data class PairedEnclosureReceivedAwaitDocumentEvent(val expectedEnclosure: String) : InlineCompletionTypingStage {
    override fun onDocumentChange(ctx: InlineCompletionTypingSession.InlineCompletionTypingSessionStageContext, event: DocumentEvent, editor: Editor): InlineCompletionTypingStage {
      if (event.newFragment.toString() != expectedEnclosure) return invalidate(ctx)
      val typingEvent = TypingEvent.PairedEnclosureInsertion(event.newFragment.toString(), event.offset)
      ctx.sendEvent(InlineCompletionEvent.DocumentChange(typingEvent, editor))
      return AwaitInitialEvent
    }
  }

  /**
   * A state that waits for a [onCaretMove] after a single character was typed.
   * In other cases (as well as in the case of a [onPairedEnclosure] insertion, because caret is not moved), the session is invalidated.
   * Check that caret moved to the same offset as the typed symbol and then send [InlineCompletionEvent.DocumentChange], otherwise [invalidate]
   */
  data class AwaitCaretMovement(val typingEvent: TypingEvent.OneSymbol) : InlineCompletionTypingStage {
    override fun onCaretMove(ctx: InlineCompletionTypingSession.InlineCompletionTypingSessionStageContext, event: CaretEvent, editor: Editor): InlineCompletionTypingStage {
      return if (typingEvent.range.startOffset == editor.logicalPositionToOffset(event.oldPosition) &&
                 typingEvent.range.endOffset == editor.logicalPositionToOffset(event.newPosition)) {
        ctx.sendEvent(InlineCompletionEvent.DocumentChange(typingEvent, editor))
        AwaitInitialEvent
      }
      else {
        invalidate(ctx)
      }
    }
  }

  /**
   * A terminal state representing an invalid or unrecognized sequence of events.
   * Can be produced by [invalidate] and the function [InlineCompletionTypingSession.InlineCompletionTypingSessionStageContext.invalidateOnUnknownChange]
   * is called before returning that state. That function ends the typing session and invalidates the inline completion session.
   *
   * @see invalidate
   */
  object Invalid : InlineCompletionTypingStage
}