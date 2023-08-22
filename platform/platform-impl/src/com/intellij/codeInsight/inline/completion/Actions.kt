// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion

import com.intellij.codeInsight.hint.HintManagerImpl
import com.intellij.codeInsight.inline.completion.InlineCompletionContext.Companion.getInlineCompletionContextOrNull
import com.intellij.codeInsight.inline.completion.InlineCompletionContext.Companion.resetInlineCompletionContext
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.actionSystem.EditorAction
import com.intellij.openapi.editor.actionSystem.EditorActionHandler
import com.intellij.openapi.editor.actionSystem.EditorWriteActionHandler
import org.jetbrains.annotations.ApiStatus

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

@ApiStatus.Experimental
class CallInlineCompletionAction : EditorAction(CallInlineCompletionHandler()), HintManagerImpl.ActionToIgnore {
  class CallInlineCompletionHandler : EditorWriteActionHandler() {
    override fun doExecute(editor: Editor, caret: Caret?, dataContext: DataContext?) {
      val curCaret = caret ?: editor.caretModel.currentCaret

      val listener = editor.getUserData(InlineCompletionHandler.KEY) ?: return
      val file = dataContext?.getData(CommonDataKeys.PSI_FILE) ?: return

      listener.invoke(editor, file, curCaret)
    }
  }
}
