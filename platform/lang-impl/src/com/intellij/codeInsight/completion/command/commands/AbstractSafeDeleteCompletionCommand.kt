// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion.command.commands

import com.intellij.codeInsight.completion.command.CommandCompletionProviderContext
import com.intellij.codeInsight.completion.command.CompletionCommand
import com.intellij.codeInsight.completion.command.HighlightInfoLookup
import com.intellij.idea.ActionsBundle
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile

abstract class AbstractSafeDeleteCompletionCommandProvider : AfterHighlightingCommandProvider,
                                                             ActionCommandProvider(actionId = "SafeDelete",
                                                                                   presentableName = ActionsBundle.message("action.SafeDelete.text"),
                                                                                   synonyms = listOf("delete", "safe delete"),
                                                                                   icon = null,
                                                                                   priority = -100,
                                                                                   previewText = ActionsBundle.message("action.SafeDelete.description")) {

  abstract fun findElement(offset: Int, psiFile: PsiFile, editor: Editor?): PsiElement?

  abstract override fun skipCommandFromHighlighting(command: CompletionCommand): Boolean

  override fun isApplicable(offset: Int, psiFile: PsiFile, editor: Editor?): Boolean {
    val element = findElement(offset, psiFile, editor) ?: return false
    editor?.caretModel?.moveToOffset(element.textRange.endOffset)
    return super.isApplicable(element.textRange.endOffset, psiFile, editor)
  }

  override fun createCommand(context: CommandCompletionProviderContext): ActionCompletionCommand? {
    val element = findElement(context.offset, context.psiFile, context.editor) ?: return null
    val startOffset = element.textRange.endOffset
    return object : ActionCompletionCommand(actionId = super.actionId,
                                            presentableActionName = super.presentableName,
                                            icon = super.icon,
                                            priority = super.priority,
                                            previewText = super.previewText,
                                            highlightInfo = HighlightInfoLookup(element.textRange, EditorColors.SEARCH_RESULT_ATTRIBUTES, 0),
                                            synonyms = super.synonyms) {
      override fun execute(offset: Int, psiFile: PsiFile, editor: Editor?) {
        editor?.caretModel?.moveToOffset(startOffset)
        super.execute(startOffset, psiFile, editor)
      }
    }
  }
}