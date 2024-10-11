// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.listeners

import com.intellij.codeInsight.inline.completion.InlineCompletion
import com.intellij.codeInsight.inline.completion.InlineCompletionEvent
import com.intellij.codeInsight.inline.completion.TypingEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.actionSystem.EditorActionHandler
import com.intellij.openapi.util.TextRange

internal class InlineCompletionEnterHandler(private val originalHandler: EditorActionHandler) : EditorActionHandler() {
  override fun doExecute(editor: Editor, caret: Caret?, dataContext: DataContext?) {
    val caret = caret ?: editor.caretModel.currentCaret
    val initialOffset = caret.offset
    originalHandler.execute(editor, caret, dataContext)

    val finalOffset = caret.offset
    val handler = InlineCompletion.getHandlerOrNull(editor)
    if (initialOffset >= finalOffset || handler == null) {
      return
    }
    val typedRange = TextRange(initialOffset, finalOffset)
    val typed = editor.document.getText(typedRange)
    val typingEvent = TypingEvent.NewLine(typed, typedRange)
    val icEvent = InlineCompletionEvent.DocumentChange(typingEvent, editor)
    handler.invokeEvent(icEvent)
  }

  override fun isEnabledForCaret(editor: Editor, caret: Caret, dataContext: DataContext?): Boolean {
    return originalHandler.isEnabled(editor, caret, dataContext)
  }
}
