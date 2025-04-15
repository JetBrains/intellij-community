// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion.command.commands

import com.intellij.codeInsight.completion.command.CommandCompletionProviderContext
import com.intellij.idea.ActionsBundle
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiFile

/**
 * A command that executes the "Comment by Line Comment" action
 * within the context of code completion.
 */
internal class LineCommentCompletionCommandProvider :
  ActionCommandProvider(actionId = "CommentByLineComment",
                        name = "Comment line",
                        i18nName = ActionsBundle.message("action.CommentByLineComment.text"),
                        icon = null,
                        priority = -100,
                        previewText = ActionsBundle.message("action.CommentByLineComment.description")) {

  override fun createCommand(context: CommandCompletionProviderContext): ActionCompletionCommand? {
    return object : ActionCompletionCommand(actionId = super.actionId,
                                            name = super.name,
                                            i18nName = super.i18nName,
                                            icon = super.icon,
                                            priority = super.priority,
                                            previewText = super.previewText) {
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
  }
}