// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion.command.commands

import com.intellij.codeInsight.CodeInsightBundle
import com.intellij.codeInsight.completion.command.CommandCompletionProviderContext
import com.intellij.codeInsight.completion.command.HighlightInfoLookup
import com.intellij.codeInsight.completion.command.getCommandContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiNameIdentifierOwner

abstract class AbstractCopyFQNCompletionCommandProvider :
  ActionCommandProvider(actionId = "CopyReference",
                        name = "Copy reference",
                        i18nName = CodeInsightBundle.message("command.completion.copy.reference.text"),
                        icon = null,
                        priority = -150,
                        previewText = null) {

  final override fun supportsReadOnly(): Boolean = true

  final override fun isApplicable(offset: Int, psiFile: PsiFile, editor: Editor?): Boolean {
    if (!super.isApplicable(offset, psiFile, editor)) return false
    val element = getCommandContext(offset, psiFile)
    if (element == null) return false
    return placeIsApplicable(element, offset)
  }

  /**
   * Determines whether a specific context within a PSI element is applicable for the command execution.
   *
   * @param element The PSI element to evaluate, or null if no context is applicable.
   * @param offset The position in the document where the applicability is being checked.
   * @return true if the place is applicable based on the provided element and offset, false otherwise.
   */
  abstract fun placeIsApplicable(element: PsiElement, offset: Int): Boolean


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
                                   previewText = CodeInsightBundle.message("command.completion.copy.reference.description", element.text),
                                   highlightInfo = HighlightInfoLookup(range, EditorColors.SEARCH_RESULT_ATTRIBUTES, 0))
  }
}
