// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.listeners.typing

import com.intellij.codeInsight.inline.completion.InlineCompletionEvent
import com.intellij.codeInsight.inline.completion.TypingEvent
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.editor.event.CaretListener
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.removeUserData
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class InlineCompletionTypingSessionTracker(
  private val sendEvent: (InlineCompletionEvent) -> Unit,
  private val invalidateOnUnknownChange: () -> Unit,
) {

  fun collectCharIfSessionActive(editor: Editor, event: DocumentEvent) {
    editor.getUserData(TYPING_SESSION_KEY)?.let { session ->
      if (session.isAlive) {
        if (event.newFragment.length != 1) {
          invalidateOnUnknownChange()
          this.endTypingSession(editor)
          return
        }
        session.collectedDocumentEvents.add(event)
      }
    }
  }

  fun startTypingSession(editor: Editor) {
    editor.caretModel.addCaretListener(caretListener)
    editor.putUserData(TYPING_SESSION_KEY, TypingSession())
  }

  fun endTypingSession(editor: Editor) {
    val typingSession = editor.getUserData(TYPING_SESSION_KEY) ?: return
    typingSession.isAlive = false
    typingSession.collectedDocumentEvents.forEach { event ->
      val typingEvent = generateTypingEvent(event, editor) ?: return@forEach
      val event = InlineCompletionEvent.DocumentChange(typingEvent, editor)
      sendEvent(event)
    }
    editor.caretModel.removeCaretListener(caretListener)
    editor.removeUserData(TYPING_SESSION_KEY)
  }

  fun isAlive(editor: Editor): Boolean = editor.getUserData(TYPING_SESSION_KEY)?.isAlive ?: false

  private fun generateTypingEvent(documentEvent: DocumentEvent, editor: Editor): TypingEvent? {
    val caretOffset = editor.caretModel.offset
    val symbol = documentEvent.newFragment.lastOrNull() ?: return null

    return when {
      caretOffset <= documentEvent.offset -> TypingEvent.PairedEnclosureInsertion(symbol.toString(), documentEvent.offset)
      else -> TypingEvent.OneSymbol(symbol, documentEvent.offset)
    }
  }

  private class TypingSession() {
    val collectedDocumentEvents: MutableList<DocumentEvent> = mutableListOf()
    val collectedCaretEvents: MutableList<CaretEvent> = mutableListOf()
    var isAlive: Boolean = true
  }

  companion object {
    private val TYPING_SESSION_KEY = Key.create<TypingSession>("inline.completion.typing.session.tracker")
    private val caretListener = object : CaretListener {
      override fun caretPositionChanged(event: CaretEvent) {
        val session = event.editor.getUserData(TYPING_SESSION_KEY) ?: return
        session.collectedCaretEvents.add(event)
        super.caretPositionChanged(event)
      }
    }
  }
}