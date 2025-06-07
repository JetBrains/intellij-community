// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.listeners

import com.intellij.codeInsight.inline.completion.InlineCompletion
import com.intellij.codeInsight.inline.completion.InlineCompletionEvent
import com.intellij.codeInsight.inline.completion.TypingEvent
import com.intellij.injected.editor.EditorWindow
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.actionSystem.EditorActionHandler
import com.intellij.openapi.util.TextRange

internal class InlineCompletionEnterHandler(private val originalHandler: EditorActionHandler) : EditorActionHandler() {
  override fun doExecute(editor: Editor, caret: Caret?, dataContext: DataContext?) {
    val currentCaret = caret ?: editor.caretModel.currentCaret
    val initialOffset = currentCaret.offset
    originalHandler.execute(editor, caret, dataContext)

    val finalOffset = currentCaret.offset
    if (initialOffset >= finalOffset) {
      return
    }
    var actualEditor = editor
    var handler = InlineCompletion.getHandlerOrNull(editor)
    var typedRange = TextRange(initialOffset, finalOffset)
    while (handler == null && actualEditor is EditorWindow) {
      typedRange = actualEditor.document.injectedToHost(typedRange)
      actualEditor = actualEditor.delegate
      handler = InlineCompletion.getHandlerOrNull(actualEditor)
    }
    if (handler == null) {
      return
    }

    val typed = actualEditor.document.getText(typedRange)
    val typingEvent = TypingEvent.NewLine(typed, typedRange)
    val icEvent = InlineCompletionEvent.DocumentChange(typingEvent, actualEditor)
    handler.invokeEvent(icEvent)
  }

  override fun isEnabledForCaret(editor: Editor, caret: Caret, dataContext: DataContext?): Boolean {
    return originalHandler.isEnabled(editor, caret, dataContext)
  }
}
