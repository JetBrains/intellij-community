// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion.commands.impl

import com.intellij.java.JavaBundle
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile

class JavaPsiElementCommentCompletionCommand : AbstractActionCompletionCommand("CommentByLineComment",
                                                                               "Comment element",
                                                                               JavaBundle.message("java.psi.element.comment.completion.command.text"),
                                                                               null) {

  override fun isApplicable(offset: Int, psiFile: PsiFile, editor: Editor?): Boolean {
    if (!super.isApplicable(offset, psiFile, editor)) return false
    val fileDocument = psiFile.fileDocument
    val immutableCharSequence = fileDocument.immutableCharSequence
    if (offset - 1 < 0) return false
    val ch = immutableCharSequence[offset - 1]
    if (ch != '}' && ch != ']' && ch != ')') return false
    val context = getHighLevelContext(offset, psiFile) ?: return false
    val startLineNumber = fileDocument.getLineNumber(context.textRange.startOffset)
    val endLineNumber = fileDocument.getLineNumber(context.textRange.endOffset)
    return startLineNumber != endLineNumber
  }

  private fun getHighLevelContext(offset: Int, psiFile: PsiFile): PsiElement? {
    var context = getContext(offset, psiFile) ?: return null
    while (context.textRange?.endOffset == offset) {
      val parent = context.parent
      if (parent != null && parent.textRange != null && parent.textRange.endOffset == offset) {
        context = parent
        continue
      }
      break
    }
    return context
  }

  override fun execute(offset: Int, psiFile: PsiFile, editor: Editor?) {
    if (editor == null) return
    val selectionModel = editor.selectionModel
    val highLevelContext = getHighLevelContext(offset, psiFile) ?: return
    val textRange = highLevelContext.textRange
    selectionModel.setSelection(textRange.startOffset, textRange.endOffset)
    super.execute(offset, psiFile, editor)
    selectionModel.removeSelection()
  }
}