// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion

import com.intellij.codeInsight.hint.HintManagerImpl
import com.intellij.codeInsight.inline.completion.InlineCompletionContext.Companion.getInlineCompletionContextOrNull
import com.intellij.codeInsight.inline.completion.InlineCompletionContext.Companion.resetInlineCompletionContext
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.actionSystem.EditorAction
import com.intellij.openapi.editor.actionSystem.EditorActionHandler
import com.intellij.openapi.editor.actionSystem.EditorWriteActionHandler
import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.editor.event.CaretListener
import com.intellij.openapi.editor.ex.FocusChangeListener
import org.jetbrains.annotations.ApiStatus
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent

@ApiStatus.Experimental
class InlineCompletionCaretListener : CaretListener {
  override fun caretPositionChanged(event: CaretEvent) {
    event.editor.resetInlineCompletionContext()
  }
}

@ApiStatus.Experimental
class InlineCompletionFocusListener : FocusChangeListener {
  override fun focusGained(editor: Editor) = Unit
  override fun focusLost(editor: Editor) {
    editor.resetInlineCompletionContext()
  }
}

@ApiStatus.Experimental
class InlineCompletionKeyListener(private val editor: Editor) : KeyAdapter() {
  private val usedKeys = listOf(
    KeyEvent.VK_ALT,
    KeyEvent.VK_OPEN_BRACKET,
    KeyEvent.VK_CLOSE_BRACKET,
    KeyEvent.VK_TAB,
  )

  override fun keyReleased(event: KeyEvent) {
    if (usedKeys.contains(event.keyCode)) {
      return
    }
    editor.resetInlineCompletionContext()
  }
}

@ApiStatus.Experimental
class InsertInlineCompletionAction : EditorAction(InsertInlineCompletionHandler()), HintManagerImpl.ActionToIgnore {
  class InsertInlineCompletionHandler : EditorWriteActionHandler() {
    override fun executeWriteAction(editor: Editor, caret: Caret?, dataContext: DataContext) {
      editor.getInlineCompletionContextOrNull()?.insert()
    }

    override fun isEnabledForCaret(editor: Editor, caret: Caret, dataContext: DataContext): Boolean {
      return editor.getInlineCompletionContextOrNull()?.startOffset == caret.offset
    }
  }
}

@ApiStatus.Experimental
class EscapeInlineCompletionHandler(val originalHandler: EditorActionHandler) : EditorActionHandler() {
  public override fun doExecute(editor: Editor, caret: Caret?, dataContext: DataContext) {
    editor.resetInlineCompletionContext()

    if (originalHandler.isEnabled(editor, caret, dataContext)) {
      originalHandler.execute(editor, caret, dataContext)
    }
  }

  public override fun isEnabledForCaret(editor: Editor, caret: Caret, dataContext: DataContext): Boolean {
    if (editor.getInlineCompletionContextOrNull() != null) {
      return true
    }

    return originalHandler.isEnabled(editor, caret, dataContext)
  }
}
