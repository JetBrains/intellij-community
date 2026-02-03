// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.listeners.typing

import com.intellij.codeInsight.inline.completion.InlineCompletionEvent
import com.intellij.codeInsight.inline.completion.TypingEvent
import com.intellij.codeInsight.inline.completion.listeners.typing.InlineCompletionTypingSessionTracker.TypingSession
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.editor.event.DocumentEvent

/**
 * Represents a state and transitions between them in the [InlineCompletionTypingSessionTracker] FSM.
 *
 * Define the specific logic for handling events and transitioning between states. By default, any unhandled event
 * in a state will transition the machine to an invalid state via [invalidate].
 * ### FSM diagram
 *
 * ```
 * [AwaitInitialEvent]
 *  ├── onDocumentChange(single char) ────────────────▶ [AwaitCaretMovement]
 *  ├── onPairedEnclosure() ───▶ [PairedEnclosureReceivedAwaitDocumentEvent]
 *  └── else ────────────────────────────────────────────────────▶ [Invalid]
 *
 * [PairedEnclosureReceivedAwaitDocumentEvent]
 *  ├── onDocumentChange(expected paired enclosure) ──▶ [AwaitInitialEvent]
 *  └── else ───────────────────────────────────────────────────▶ [Invalid]
 *
 * [AwaitCaretMovement]
 *  ├── onCaretMove(matching position) ───────────────▶ [AwaitInitialEvent]
 *  └── else ───────────────────────────────────────────────────▶ [Invalid]
 *
 * [Invalid]
 *  └── (Terminal state: no recovery)
 * ```
 * @see TypingSession.TypingSessionStateContext
 * @see InlineCompletionTypingSessionTracker
 */

internal sealed class InlineCompletionTypingState {

  open fun onPairedEnclosure(
    ctx: TypingSession.TypingSessionStateContext,
    expectedEnclosure: String,
  ): InlineCompletionTypingState = invalidate(ctx)

  open fun onDocumentChange(
    ctx: TypingSession.TypingSessionStateContext,
    event: DocumentEvent,
    editor: Editor,
  ): InlineCompletionTypingState = invalidate(ctx)

  open fun onCaretMove(
    ctx: TypingSession.TypingSessionStateContext,
    event: CaretEvent,
    editor: Editor,
  ): InlineCompletionTypingState = invalidate(ctx)

  /**
   * Centralized function to handle the transition to an invalid state.
   */
  fun invalidate(ctx: TypingSession.TypingSessionStateContext): InlineCompletionTypingState {
    ctx.invalidateOnUnknownChange()
    return Invalid
  }

  /**
   * The initial state of the session, waiting for the first event.
   * The first event can be either a paired enclosure or a single symbol
   */
  data object AwaitInitialEvent : InlineCompletionTypingState() {
    override fun onPairedEnclosure(
      ctx: TypingSession.TypingSessionStateContext,
      expectedEnclosure: String,
    ): InlineCompletionTypingState = PairedEnclosureReceivedAwaitDocumentEvent(expectedEnclosure)

    override fun onDocumentChange(
      ctx: TypingSession.TypingSessionStateContext,
      event: DocumentEvent,
      editor: Editor,
    ): InlineCompletionTypingState {
      val symbol = event.newFragment.singleOrNull() ?: return invalidate(ctx)
      val typingEvent = TypingEvent.OneSymbol(symbol, event.offset)
      return AwaitCaretMovement(typingEvent)
    }
  }

  /**
   * A state entered after the [InlineCompletionTypedHandlerDelegate] has signaled a paired enclosure insertion.
   * In that case, we know that the enclosure was inserted, and we can send [InlineCompletionEvent.DocumentChange],
   * but if the caret will be moved, [invalidate] will be called in next step, because [AwaitInitialEvent] doesn't handle caret movements.
   */
  data class PairedEnclosureReceivedAwaitDocumentEvent(val expectedEnclosure: String) : InlineCompletionTypingState() {
    override fun onDocumentChange(
      ctx: TypingSession.TypingSessionStateContext,
      event: DocumentEvent,
      editor: Editor,
    ): InlineCompletionTypingState {
      if (event.newFragment.toString() != expectedEnclosure) return invalidate(ctx)
      val typingEvent = TypingEvent.PairedEnclosureInsertion(event.newFragment.toString(), event.offset)
      ctx.sendEvent(InlineCompletionEvent.DocumentChange(typingEvent, editor))
      return AwaitInitialEvent
    }
  }

  /**
   * State can be [InlineCompletionTypingState.AwaitCaretMovement] only if the previous state was AwaitInitialEvent.
   * After checking in movement was expected, we can send [InlineCompletionEvent.DocumentChange]
   */
  data class AwaitCaretMovement(val typingEvent: TypingEvent.OneSymbol) : InlineCompletionTypingState() {
    override fun onCaretMove(
      ctx: TypingSession.TypingSessionStateContext,
      event: CaretEvent,
      editor: Editor,
    ): InlineCompletionTypingState {
      return if (event.matchTypingEvent(typingEvent)) {
        ctx.sendEvent(InlineCompletionEvent.DocumentChange(typingEvent, editor))
        AwaitInitialEvent
      }
      else {
        invalidate(ctx)
      }
    }

    private fun CaretEvent.matchTypingEvent(typingEvent: TypingEvent.OneSymbol): Boolean {
      return (typingEvent.range.startOffset == editor.logicalPositionToOffset(oldPosition) &&
              typingEvent.range.endOffset == editor.logicalPositionToOffset(newPosition))
    }
  }

  /**
   * A terminal state representing an invalid sequence of events.
   * Can be produced only by [invalidate].
   * @see invalidate
   */
  data object Invalid : InlineCompletionTypingState()
}