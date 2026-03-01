// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion

import com.intellij.codeInsight.inline.completion.listeners.typing.InlineCompletionTemplateListener
import com.intellij.codeInsight.template.impl.editorActions.TypedActionHandlerBase
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorModificationUtil
import com.intellij.openapi.editor.actionSystem.TypedActionHandler
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiUtilBase

internal class InlineCompletionTypedHandler(originalHandler: TypedActionHandler?) : TypedActionHandlerBase(originalHandler) {

  /**
   * Executes the original handler and starts a typing session if the editor is in the correct state.
   *
   * During the typing session, [com.intellij.codeInsight.inline.completion.listeners.typing.InlineCompletionTypingSessionTracker]
   * follows the session-related events and send [InlineCompletionRequest]s if received events are valid.
   *
   */
  override fun execute(editor: Editor, charTyped: Char, dataContext: DataContext) {
    val project = CommonDataKeys.PROJECT.getData(dataContext)
    val file: PsiFile? = if (project == null) null else PsiUtilBase.getPsiFileInEditor(editor, project)

    if (file == null) {
      myOriginalHandler?.execute(editor, charTyped, dataContext)
      return
    }

    if (InlineCompletionTemplateListener.isInlineRefactoringInProgress(editor)) {
      myOriginalHandler?.execute(editor, charTyped, dataContext)
      return // ML-1684 Do now show inline completion while refactoring
    }

    if (!EditorModificationUtil.checkModificationAllowed(editor)) {
      return
    }
    val typingSessionTracker = InlineCompletion.getHandlerOrNull(editor)?.typingSessionTracker

    try {
      typingSessionTracker?.startTypingSession(editor)
      myOriginalHandler?.execute(editor, charTyped, dataContext)
    }
    finally {
      typingSessionTracker?.endTypingSession(editor)
    }
  }
}