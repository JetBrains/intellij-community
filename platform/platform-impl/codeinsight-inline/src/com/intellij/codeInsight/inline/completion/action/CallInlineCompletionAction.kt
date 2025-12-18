// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.action

import com.intellij.codeInsight.hint.HintManagerImpl
import com.intellij.codeInsight.inline.completion.InlineCompletion
import com.intellij.codeInsight.inline.completion.InlineCompletionEvent
import com.intellij.codeInsight.inline.edit.NoInlineEditShownNotifier
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.actionSystem.EditorAction
import com.intellij.openapi.editor.actionSystem.EditorActionHandler
import com.intellij.openapi.editor.actionSystem.EditorWriteActionHandler
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class CallInlineCompletionAction : EditorAction(CallInlineCompletionHandler()), HintManagerImpl.ActionToIgnore {

  class CallInlineCompletionHandler : EditorWriteActionHandler() {
    override fun doExecute(editor: Editor, caret: Caret?, dataContext: DataContext?) {
      val curCaret = caret ?: editor.caretModel.currentCaret

      val handler = InlineCompletion.getHandlerOrNull(editor) ?: return
      handler.invokeEvent(InlineCompletionEvent.DirectCall(editor, curCaret, dataContext))
    }

    override fun isEnabledForCaret(editor: Editor, caret: Caret, dataContext: DataContext?): Boolean {
      return InlineCompletion.getHandlerOrNull(editor) != null
    }
  }

  internal class ShowNoSuggestionsHintIfNeededHandler(private val originalHandler: EditorActionHandler) : EditorWriteActionHandler() {
    override fun doExecute(editor: Editor, caret: Caret?, dataContext: DataContext?) {
      if (originalHandler.isEnabled(editor, caret, dataContext)) {
        originalHandler.execute(editor, caret, dataContext)
      }

      if (InlineCompletion.getHandlerOrNull(editor) != null) {
        val project = dataContext?.getData(CommonDataKeys.PROJECT) ?: editor.project
        project?.let { project ->
          NoInlineEditShownNotifier.getInstance(project).notifyNoSuggestionIfNothingIsShown(editor)
        }
      }
    }

    override fun isEnabledForCaret(editor: Editor, caret: Caret, dataContext: DataContext?): Boolean {
      return InlineCompletion.getHandlerOrNull(editor) != null || originalHandler.isEnabled(editor, caret, dataContext)
    }
  }
}
