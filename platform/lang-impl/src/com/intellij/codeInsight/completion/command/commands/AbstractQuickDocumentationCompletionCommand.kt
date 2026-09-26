// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion.command.commands

import com.intellij.codeInsight.completion.command.CommandCompletionProviderContext
import com.intellij.codeInsight.completion.command.HighlightInfoLookup
import com.intellij.idea.ActionsBundle
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile

abstract class AbstractQuickDocumentationCompletionCommand :
  ActionCommandProvider(actionId = "QuickJavaDoc",
                        presentableName = ActionsBundle.message("action.QuickJavaDoc.text"),
                        synonyms = listOf("documentation", "quick documentation"),
                        icon = null,
                        priority = -100,
                        previewText = ActionsBundle.message("action.QuickJavaDoc.description")) {

  abstract fun findElement(offset: Int, psiFile: PsiFile): PsiElement?

  override fun createCommand(context: CommandCompletionProviderContext): ActionCompletionCommand? {
    val element = findElement(context.offset, context.psiFile) ?: return null
    if (element.textRange == null) return null
    return ActionCompletionCommand(actionId = super.actionId,
                                   presentableActionName = super.presentableName,
                                   icon = super.icon,
                                   priority = super.priority,
                                   previewText = super.previewText,
                                   synonyms = super.synonyms,
                                   highlightInfo = HighlightInfoLookup(element.textRange, EditorColors.SEARCH_RESULT_ATTRIBUTES, 0))
  }
}