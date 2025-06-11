// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion.command.commands

import com.intellij.codeInsight.completion.command.CommandCompletionProviderContext
import com.intellij.codeInsight.completion.command.HighlightInfoLookup
import com.intellij.codeInsight.completion.command.getTargetContext
import com.intellij.idea.ActionsBundle
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.psi.*

abstract class AbstractRenameActionCommandProvider : ActionCommandProvider(actionId = IdeActions.ACTION_RENAME,
                                                                           presentableName = ActionsBundle.message("action.RenameElement.text"),
                                                                           previewText = ActionsBundle.message("action.RenameElement.description"),
                                                                           synonyms = listOf("Rename", "Change name")) {
  override fun isApplicable(offset: Int, psiFile: PsiFile, editor: Editor?): Boolean {
    val offset = findRenameOffset(offset, psiFile) ?: return false
    editor?.caretModel?.moveToOffset(offset)
    if (!super.isApplicable(offset, psiFile, editor)) return false
    val result = getTargetElement(editor, offset)
    return result != null
  }

  abstract fun findRenameOffset(offset: Int, psiFile: PsiFile): Int?

  override fun createCommand(context: CommandCompletionProviderContext): ActionCompletionCommand? {
    val offset = findRenameOffset(context.offset, context.psiFile) ?: return null
    context.editor.caretModel.moveToOffset(offset)
    var element = getTargetElement(context.editor, offset)
    if (element is PsiNameIdentifierOwner) {
      element = element.nameIdentifier
    }
    return object : ActionCompletionCommand(actionId = super.actionId,
                                            synonyms = super.synonyms,
                                            presentableActionName = super.presentableName,
                                            icon = super.icon,
                                            priority = super.priority,
                                            previewText = super.previewText,
                                            highlightInfo = if (element != null && element.textRange != null)
                                              HighlightInfoLookup(element.textRange, EditorColors.SEARCH_RESULT_ATTRIBUTES, 0)
                                            else null) {
      override fun execute(offset: Int, psiFile: PsiFile, editor: Editor?) {
        val targetOffset = findRenameOffset(offset, psiFile) ?: return
        editor?.caretModel?.moveToOffset(targetOffset)
        super.execute(offset, psiFile, editor)
      }
    }
  }

  private fun getTargetElement(editor: Editor?, targetOffset: Int): PsiElement? {
    if (editor == null) return null
    val context = getTargetContext(targetOffset, editor)
    if (context == null) return null
    if (context is PsiCompiledElement || context !is PsiNamedElement) return null
    return context
  }
}