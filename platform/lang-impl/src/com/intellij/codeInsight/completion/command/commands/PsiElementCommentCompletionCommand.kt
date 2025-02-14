// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion.command.commands

import com.intellij.codeInsight.CodeInsightBundle
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil

internal class PsiElementCommentCompletionCommand : AbstractActionCompletionCommand("CommentByLineComment",
                                                                                    "Comment element",
                                                                                    CodeInsightBundle.message(
                                                                                      "command.completion.psi.element.comment.text"),
                                                                                    null) {

  override fun isApplicable(offset: Int, psiFile: PsiFile, editor: Editor?): Boolean {
    if (!super.isApplicable(offset, psiFile, editor)) return false
    val fileDocument = psiFile.fileDocument
    if (offset - 1 < 0) return false
    val context = getHighLevelContext(offset, psiFile) ?: return false
    if (PsiTreeUtil.skipWhitespacesBackward(context) is PsiComment) return true
    val startLineNumber = fileDocument.getLineNumber(context.textRange.startOffset)
    val endLineNumber = fileDocument.getLineNumber(context.textRange.endOffset)
    return startLineNumber != endLineNumber
  }

  private fun getHighLevelContext(offset: Int, psiFile: PsiFile): PsiElement? {
    var context = getContext(offset, psiFile) ?: return null
    if (context.textRange.endOffset != offset) return null
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
    var startOffset = textRange.startOffset
    val endOffset = textRange.endOffset
    var current: PsiElement? = highLevelContext
    while (current is PsiComment) {
      startOffset = current.textRange.endOffset
      current = PsiTreeUtil.skipWhitespacesBackward(current)
    }
    selectionModel.setSelection(startOffset, endOffset)
    super.execute(offset, psiFile, editor)
    selectionModel.removeSelection()
  }
}