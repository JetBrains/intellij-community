// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion

import com.intellij.codeInsight.hint.HintManagerImpl
import com.intellij.codeInsight.inline.completion.logs.InlineCompletionUsageTracker.ShownEvents.FinishType
import com.intellij.codeInsight.inline.completion.session.InlineCompletionContext
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.actionSystem.EditorAction
import com.intellij.openapi.editor.actionSystem.EditorActionHandler
import com.intellij.openapi.editor.actionSystem.EditorWriteActionHandler

class InsertInlineCompletionAction : EditorAction(InsertInlineCompletionHandler()), HintManagerImpl.ActionToIgnore {
  class InsertInlineCompletionHandler : EditorWriteActionHandler() {
    override fun executeWriteAction(editor: Editor, caret: Caret?, dataContext: DataContext) {
      InlineCompletion.getHandlerOrNull(editor)?.insert()
    }

    override fun isEnabledForCaret(editor: Editor, caret: Caret, dataContext: DataContext): Boolean {
      return InlineCompletionContext.getOrNull(editor)?.startOffset() == caret.offset
    }
  }
}

abstract class CancellationKeyInlineCompletionHandler(val originalHandler: EditorActionHandler,
                                                      val finishType: FinishType) : EditorActionHandler() {
  public override fun doExecute(editor: Editor, caret: Caret?, dataContext: DataContext) {
    val context = InlineCompletionContext.getOrNull(editor) ?: run {
      if (originalHandler.isEnabled(editor, caret, dataContext)) {
        originalHandler.execute(editor, caret, dataContext)
      }
      return
    }
    InlineCompletion.getHandlerOrNull(editor)?.hide(context, finishType)

    if (originalHandler.isEnabled(editor, caret, dataContext)) {
      originalHandler.execute(editor, caret, dataContext)
    }
  }

  public override fun isEnabledForCaret(editor: Editor, caret: Caret, dataContext: DataContext): Boolean {
    if (InlineCompletionContext.getOrNull(editor) != null) {
      return true
    }

    return originalHandler.isEnabled(editor, caret, dataContext)
  }
}

class EscapeInlineCompletionHandler(originalHandler: EditorActionHandler) :
  CancellationKeyInlineCompletionHandler(originalHandler, FinishType.ESCAPE_PRESSED)

class BackSpaceInlineCompletionHandler(originalHandler: EditorActionHandler) :
  CancellationKeyInlineCompletionHandler(originalHandler, FinishType.BACKSPACE_PRESSED)

class CallInlineCompletionAction : EditorAction(CallInlineCompletionHandler()), HintManagerImpl.ActionToIgnore {
  class CallInlineCompletionHandler : EditorWriteActionHandler() {
    override fun doExecute(editor: Editor, caret: Caret?, dataContext: DataContext?) {
      val curCaret = caret ?: editor.caretModel.currentCaret

      val listener = InlineCompletion.getHandlerOrNull(editor) ?: return
      listener.invoke(InlineCompletionEvent.DirectCall(editor, curCaret, dataContext))
    }
  }
}
