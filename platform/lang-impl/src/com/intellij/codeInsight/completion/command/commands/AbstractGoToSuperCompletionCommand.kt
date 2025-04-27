// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion.command.commands

import com.intellij.codeInsight.completion.command.CommandCompletionProviderContext
import com.intellij.codeInsight.completion.command.HighlightInfoLookup
import com.intellij.codeInsight.completion.command.getCommandContext
import com.intellij.idea.ActionsBundle
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiNameIdentifierOwner

abstract class AbstractGoToSuperMethodCompletionCommandProvider :
  ActionCommandProvider(actionId = "GotoSuperMethod",
                        name = "Go to Super Method",
                        i18nName = ActionsBundle.message("action.GotoSuperMethod.text"),
                        icon = null,
                        priority = -100,
                        previewText = ActionsBundle.message("action.GotoSuperMethod.description")) {
  override fun isApplicable(offset: Int, psiFile: PsiFile, editor: Editor?): Boolean {
    val element = getCommandContext(offset, psiFile) ?: return false
    return canGoToSuperMethod(element, offset)
  }

  override fun supportsReadOnly(): Boolean = true

  /**
   * Determines if navigation to the super method of the specified element is possible.
   *
   * @param element the PSI element for which the super method is to be checked; can be null
   * @param offset the offset within the element's document, typically representing the caret's position
   * @return true if navigation to the super method is possible, otherwise false
   */
  abstract fun canGoToSuperMethod(element: PsiElement, offset: Int): Boolean


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
}