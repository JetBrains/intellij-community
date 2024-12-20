// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion.commands.impl

import com.intellij.idea.ActionsBundle
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiFile

class CommentCompletionCommand : AbstractActionCompletionCommand("CommentByLineComment",
                                                                 "Comment",
                                                                 ActionsBundle.actionText("CommentByLineComment"),
                                                                 null) {
  override fun execute(offset: Int, psiFile: PsiFile, editor: Editor?) {
    if (editor == null) return
    val selectionModel = editor.selectionModel
    val hasSelection = selectionModel.hasSelection()
    if (!hasSelection) {
      val carets = editor.caretModel.allCarets
      if (carets.size == 1) {
        val caret = carets[0]
        val document = editor.document
        val lineNumber = document.getLineNumber(offset)
        val lineStartOffset = document.getLineStartOffset(lineNumber)
        caret.setSelection(lineStartOffset, offset)
      }
    }
    super.execute(offset, psiFile, editor)
    if (!hasSelection) selectionModel.removeSelection()
  }
}