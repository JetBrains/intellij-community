// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.listeners.typing

import com.intellij.codeInsight.inline.completion.InlineCompletion
import com.intellij.codeInsight.inline.completion.TypingEvent
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.removeUserData
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class InlineCompletionTypingSessionTracker {

  fun collectCharIfSessionActive(editor: Editor, event: DocumentEvent) {
    editor.getUserData(TYPING_SESSION_KEY)?.let { session ->
      if (session.isAlive) {
        session.collectedEvents.add(event)

        val lastSymbol = event.newFragment.lastOrNull() ?: return@let
        val handler = InlineCompletion.getHandlerOrNull(editor) ?: return@let

        if (!handler.documentChangesTracker.lastEventIsPairedEnclosure()) {
          handler.documentChangesTracker.allowTyping(TypingEvent.OneSymbol(lastSymbol, event.offset))
        }
        handler.documentChangesTracker.onDocumentEvent(event, editor)
      }
    }
  }

  fun startTypingSession(editor: Editor) {
    editor.putUserData(TYPING_SESSION_KEY, TypingSession())
  }

  fun endTypingSession(editor: Editor) {
    editor.removeUserData(TYPING_SESSION_KEY)
  }

  private class TypingSession() {
    val collectedEvents: MutableList<DocumentEvent> = mutableListOf()
    var isAlive: Boolean = true
  }

  companion object {
    private val TYPING_SESSION_KEY = Key.create<TypingSession>("inline.completion.typing.session.tracker")
  }
}