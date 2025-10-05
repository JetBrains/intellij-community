// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion.command.commands

import com.intellij.codeInsight.completion.command.CommandCompletionProviderContext
import com.intellij.idea.ActionsBundle
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiFile

abstract class AbstractInlineMethodCompletionCommandProvider :
  ActionCommandProvider(actionId = "Inline",
                        synonyms = listOf("Inline"),
                        presentableName = ActionsBundle.message("action.Inline.text"),
                        icon = null,
                        priority = -150,
                        previewText = ActionsBundle.message("action.Inline.description")) {
  override fun isApplicable(offset: Int, psiFile: PsiFile, editor: Editor?): Boolean {
    val offsetToCall = findOffsetToCall(offset, psiFile)
    if (offsetToCall != null && offsetToCall != offset) return true
    if (!super.isApplicable(offset, psiFile, editor)) return false
    return offsetToCall != null
  }

  override fun createCommand(context: CommandCompletionProviderContext): ActionCompletionCommand? {
    return object : ActionCompletionCommand(actionId = super.actionId,
                                            synonyms = super.synonyms,
                                            presentableActionName = super.presentableName,
                                            icon = super.icon,
                                            priority = super.priority,
                                            previewText = super.previewText) {

      override fun execute(offset: Int, psiFile: PsiFile, editor: Editor?) {
        val targetOffset = findOffsetToCall(offset, psiFile) ?: return
        val fileDocument = psiFile.fileDocument
        val rangeMarker = fileDocument.createRangeMarker(offset, offset)
        editor?.caretModel?.moveToOffset(targetOffset)
        super.execute(offset, psiFile, editor)
        if (rangeMarker.isValid) {
          editor?.caretModel?.moveToOffset(rangeMarker.startOffset)
        }
      }
    }
  }

  abstract fun findOffsetToCall(offset: Int, psiFile: PsiFile): Int?
}
