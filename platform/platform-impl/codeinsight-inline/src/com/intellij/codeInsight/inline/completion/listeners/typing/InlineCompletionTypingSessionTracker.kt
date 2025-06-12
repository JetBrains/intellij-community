// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.listeners.typing

import com.intellij.codeInsight.inline.completion.InlineCompletionEvent
import com.intellij.codeInsight.inline.completion.TypingEvent
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.removeUserData
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class InlineCompletionTypingSessionTracker(
  private val sendEvent: (InlineCompletionEvent) -> Unit,
  private val invalidateOnUnknownChange: () -> Unit,
) {

  @RequiresEdt
  fun collectCharIfSessionActive(event: DocumentEvent, editor: Editor) {
    ThreadingAssertions.assertEventDispatchThread()
    editor.getUserData(TYPING_SESSION_KEY)?.takeIf { it.isAlive && event.newFragment.length == 1 }?.let { session ->
      val typingEvent = generateTypingEvent(event, editor) ?: return@let
      val event = InlineCompletionEvent.DocumentChange(typingEvent, editor)
      sendEvent(event)
    } ?: {
      endTypingSession(editor)
      invalidateOnUnknownChange()
    }
  }

  @RequiresEdt
  fun startTypingSession(editor: Editor) {
    ThreadingAssertions.assertEventDispatchThread()
    //editor.caretModel.addCaretListener(caretListener)
    editor.putUserData(TYPING_SESSION_KEY, TypingSession())
  }

  @RequiresEdt
  fun endTypingSession(editor: Editor) {
    ThreadingAssertions.assertEventDispatchThread()
    val typingSession = editor.getUserData(TYPING_SESSION_KEY) ?: return
    typingSession.isAlive = false
    editor.removeUserData(TYPING_SESSION_KEY)
  }

  @RequiresEdt
  fun isAlive(editor: Editor): Boolean {
    ThreadingAssertions.assertEventDispatchThread()
    return editor.getUserData(TYPING_SESSION_KEY)?.isAlive ?: false
  }

  @RequiresEdt
  fun markNextEventAsClosingBracket(editor: Editor) {
    ThreadingAssertions.assertEventDispatchThread()
    val typingSession = editor.getUserData(TYPING_SESSION_KEY) ?: return
    typingSession.nextEventIsClosingEnclosure = true
  }

  private fun generateTypingEvent(documentEvent: DocumentEvent, editor: Editor): TypingEvent? {
    val symbol = documentEvent.newFragment.lastOrNull() ?: return null
    val typingSession = editor.getUserData(TYPING_SESSION_KEY) ?: return null

    return if (typingSession.nextEventIsClosingEnclosure)
      TypingEvent.PairedEnclosureInsertion(symbol.toString(), documentEvent.offset)
    else
      TypingEvent.OneSymbol(symbol, documentEvent.offset)
  }

  private class TypingSession() {
    val collectedDocumentEvents: MutableList<DocumentEvent> = mutableListOf()
    val collectedCaretEvents: MutableList<CaretEvent> = mutableListOf()
    var isAlive: Boolean = true
    var nextEventIsClosingEnclosure: Boolean = false
  }

  companion object {
    private val TYPING_SESSION_KEY = Key.create<TypingSession>("inline.completion.typing.session.tracker")
    //private val caretListener = object : CaretListener {
    //  override fun caretPositionChanged(event: CaretEvent) {
    //    val session = event.editor.getUserData(TYPING_SESSION_KEY) ?: return
    //    session.collectedCaretEvents.add(event)
    //    super.caretPositionChanged(event)
    //  }
    //}
  }
}