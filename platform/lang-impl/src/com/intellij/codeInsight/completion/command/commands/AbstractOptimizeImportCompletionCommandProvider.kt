// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion.command.commands

import com.intellij.codeInsight.completion.command.CommandCompletionProviderContext
import com.intellij.codeInsight.completion.command.HighlightInfoLookup
import com.intellij.idea.ActionsBundle
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile

abstract class AbstractOptimizeImportCompletionCommandProvider :
  ActionCommandProvider(actionId = "OptimizeImports",
                        synonyms = listOf("Optimize imports"),
                        presentableName = ActionsBundle.message("action.OptimizeImports.text"),
                        icon = null,
                        priority = -100,
                        previewText = ActionsBundle.message("action.OptimizeImports.description")) {

  override fun isApplicable(offset: Int, psiFile: PsiFile, editor: Editor?): Boolean {
    if (!super.isApplicable(offset, psiFile, editor)) return false
    if (InjectedLanguageManager.getInstance(psiFile.project).isInjectedFragment(psiFile)) return false
    return isApplicableToProject(offset, psiFile) || isImportList(psiFile, offset)
  }

  override fun createCommand(context: CommandCompletionProviderContext): ActionCompletionCommand? {
    val range: TextRange? = getTextRangeImportList(context.psiFile, context.offset)
    return ActionCompletionCommand(actionId = super.actionId,
                                   presentableActionName = super.presentableName,
                                   icon = super.icon,
                                   priority = super.priority,
                                   previewText = super.previewText,
                                   synonyms = super.synonyms,
                                   highlightInfo = if (range != null) {
                                     HighlightInfoLookup(range, EditorColors.SEARCH_RESULT_ATTRIBUTES, 0)
                                   }
                                   else {
                                     null
                                   })
  }

  abstract fun isImportList(psiFile: PsiFile, offset: Int): Boolean
  abstract fun getTextRangeImportList(psiFile: PsiFile, offset: Int): TextRange?
}