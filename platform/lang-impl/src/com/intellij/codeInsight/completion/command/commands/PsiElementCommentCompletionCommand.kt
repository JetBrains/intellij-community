// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion.command.commands

import com.intellij.codeInsight.CodeInsightBundle
import com.intellij.codeInsight.completion.command.CommandCompletionProviderContext
import com.intellij.codeInsight.completion.command.HighlightInfoLookup
import com.intellij.codeInsight.completion.command.getCommandContext
import com.intellij.idea.ActionsBundle
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil


internal class PsiElementCommentByBlockCompletionCommandProvider : ActionCommandProvider(
  actionId = "CommentByBlockComment",
  name = "Comment/uncomment by block comment",
  i18nName = CodeInsightBundle.message("command.completion.psi.element.comment.block.text"),
  previewText = ActionsBundle.message("action.CommentByBlockComment.description")) {
  override fun isApplicable(offset: Int, psiFile: PsiFile, editor: Editor?): Boolean {
    if (!super.isApplicable(offset, psiFile, editor)) return false
    if (offset - 1 < 0) return false
    getHighLevelContext(offset, psiFile) ?: return false
    return true
  }

  override fun createCommand(context: CommandCompletionProviderContext): ActionCompletionCommand? {
    val element = getHighLevelContext(context.offset, context.psiFile) ?: return null
    val range = element.textRange ?: return null
    return object : ActionCompletionCommand(actionId = super.actionId,
                                            name = super.name,
                                            i18nName = super.i18nName,
                                            icon = super.icon,
                                            priority = super.priority,
                                            previewText = super.previewText,
                                            highlightInfo =
                                              HighlightInfoLookup(range, EditorColors.SEARCH_RESULT_ATTRIBUTES, 0)) {
      override fun execute(offset: Int, psiFile: PsiFile, editor: Editor?) {
        if (editor == null) return
        val selectionModel = editor.selectionModel
        val highLevelContext = getHighLevelContext(offset, psiFile) ?: return
        val textRange = highLevelContext.textRange
        val startOffset = textRange.startOffset
        val endOffset = textRange.endOffset
        selectionModel.setSelection(startOffset, endOffset)
        super.execute(offset, psiFile, editor)
        selectionModel.removeSelection()
      }
    }
  }
}

internal class PsiElementCommentByLineCompletionCommandProvider : ActionCommandProvider(
  actionId = "CommentByLineComment",
  name = "Comment by line comment",
  i18nName = CodeInsightBundle.message("command.completion.psi.element.comment.line.text"),
  previewText = ActionsBundle.message("action.CommentByLineComment.description")) {
  override fun isApplicable(offset: Int, psiFile: PsiFile, editor: Editor?): Boolean {
    if (!super.isApplicable(offset, psiFile, editor)) return false
    val fileDocument = psiFile.fileDocument
    if (offset - 1 < 0) return false
    val context = getHighLevelContext(offset, psiFile) ?: return false
    if (context is PsiComment) return false
    if (PsiTreeUtil.skipWhitespacesBackward(context) is PsiComment) return true
    val startLineNumber = fileDocument.getLineNumber(context.textRange.startOffset)
    val endLineNumber = fileDocument.getLineNumber(context.textRange.endOffset)
    if (startLineNumber == endLineNumber) return false
    val lineStartOffset = fileDocument.getLineStartOffset(startLineNumber)
    for (i in lineStartOffset until context.textRange.startOffset) {
      if (!Character.isWhitespace(fileDocument.charsSequence[i])) return false
    }
    return true
  }

  override fun createCommand(context: CommandCompletionProviderContext): ActionCompletionCommand? {
    val element = getHighLevelContext(context.offset, context.psiFile) ?: return null
    val range = element.textRange ?: return null
    return object : ActionCompletionCommand(actionId = super.actionId,
                                            name = super.name,
                                            i18nName = super.i18nName,
                                            icon = super.icon,
                                            priority = super.priority,
                                            previewText = super.previewText,
                                            highlightInfo =
                                              HighlightInfoLookup(range, EditorColors.SEARCH_RESULT_ATTRIBUTES, 0)) {
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
  }
}


private fun getHighLevelContext(offset: Int, psiFile: PsiFile): PsiElement? {
  var context = getCommandContext(offset, psiFile) ?: return null
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