// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion.command.commands

import com.intellij.codeInsight.completion.command.CommandCompletionProviderContext
import com.intellij.codeInsight.completion.command.HighlightInfoLookup
import com.intellij.codeInsight.completion.command.getCommandContext
import com.intellij.find.actions.ShowUsagesAction
import com.intellij.idea.ActionsBundle
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiNameIdentifierOwner

abstract class AbstractShowUsagesActionCompletionCommandProvider :
  ActionCommandProvider(actionId = ShowUsagesAction.ID,
                        name = "Show usages",
                        i18nName = ActionsBundle.message("action.ShowUsages.text"),
                        icon = null,
                        priority = -100,
                        previewText = ActionsBundle.message("action.ShowUsages.description")) {
  final override fun supportsReadOnly(): Boolean = true
  final override fun isApplicable(offset: Int, psiFile: PsiFile, editor: Editor?): Boolean {
    val element = getCommandContext(offset, psiFile) ?: return false
    return super.isApplicable(offset, psiFile, editor) && hasToShow(element) &&
           !InjectedLanguageManager.getInstance(psiFile.project).isInjectedFragment(psiFile)
  }


  override fun createCommand(context: CommandCompletionProviderContext): ActionCompletionCommand? {
    var element = getCommandContext(context.offset, context.psiFile) ?: return null
    if (element is PsiNameIdentifierOwner) {
      element = element.nameIdentifier ?: return null
    }
    val range = element.textRange ?: return null
    return ActionCompletionCommand(actionId = super.actionId,
                                   name = super.name,
                                   i18nName = super.i18nName,
                                   icon = super.icon,
                                   priority = super.priority,
                                   previewText = super.previewText,
                                   highlightInfo = HighlightInfoLookup(range, EditorColors.SEARCH_RESULT_ATTRIBUTES, 0))
  }

  /**
   * Determines whether a given PSI element should be used to show its usages
   * in the context of the command execution.
   *
   * @param element the PSI element being checked; may be null
   * @return true if the element should be shown, false otherwise
   */
  abstract fun hasToShow(element: PsiElement): Boolean
}