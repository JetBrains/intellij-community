// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion.command.commands

import com.intellij.codeInsight.completion.command.CommandCompletionProviderContext
import com.intellij.codeInsight.completion.command.HighlightInfoLookup
import com.intellij.codeInsight.completion.command.getTargetContext
import com.intellij.idea.ActionsBundle
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiNameIdentifierOwner
import com.intellij.psi.PsiNamedElement

internal class RenameActionCommandProvider : ActionCommandProvider(actionId = IdeActions.ACTION_RENAME,
                                                                   name = "Rename",
                                                                   i18nName = ActionsBundle.message("action.RenameElement.text"),
                                                                   previewText = ActionsBundle.message("action.RenameElement.description"),
                                                                   synonyms = listOf("Rename", "Change name")) {
  override fun isApplicable(offset: Int, psiFile: PsiFile, editor: Editor?): Boolean {
    if (!super.isApplicable(offset, psiFile, editor)) return false
    val result = getTargetElement(editor, offset)
    return result != null
  }

  override fun createCommand(context: CommandCompletionProviderContext): ActionCompletionCommand {
    var element = getTargetElement(context.editor, context.offset)
    if (element is PsiNameIdentifierOwner) {
      element = element.nameIdentifier
    }
    return ActionCompletionCommand(actionId = super.actionId,
                                   name = super.name,
                                   i18nName = super.i18nName,
                                   icon = super.icon,
                                   priority = super.priority,
                                   previewText = super.previewText,
                                   highlightInfo = if (element != null && element.textRange != null)
                                     HighlightInfoLookup(element.textRange, EditorColors.SEARCH_RESULT_ATTRIBUTES, 0)
                                   else null)
  }

  private fun getTargetElement(editor: Editor?, targetOffset: Int): PsiElement? {
    if (editor == null) return null
    val context = getTargetContext(targetOffset, editor)
    if (context == null) return null
    if (!context.isWritable || context !is PsiNamedElement) return null
    return context
  }
}