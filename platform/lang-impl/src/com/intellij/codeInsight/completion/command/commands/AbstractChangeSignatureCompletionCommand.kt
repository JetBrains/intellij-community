// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion.command.commands

import com.intellij.codeInsight.completion.command.CommandCompletionProviderContext
import com.intellij.idea.ActionsBundle
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiFile

abstract class AbstractChangeSignatureCompletionCommandProvider : ActionCommandProvider(
  actionId = "ChangeSignature",
  name = "Change signature",
  i18nName = ActionsBundle.message("action.ChangeSignature.text"),
  icon = null,
  priority = -100,
  previewText = ActionsBundle.message("action.ChangeSignature.description"),
  synonyms = listOf("Change definition", "Change parameters")
) {

  override fun isApplicable(offset: Int, psiFile: PsiFile, editor: Editor?): Boolean {
    val offset = findChangeSignatureOffset(offset, psiFile) ?: return false
    editor?.caretModel?.moveToOffset(offset)
    return super.isApplicable(offset, psiFile, editor)
  }


  /**
   * Finds and identifies a potential "Change Signature" action at the specified position
   * within the given file.
   *
   * @param offset The position within the file where the "Change Signature" action may be applicable.
   * @param file The PsiFile instance representing the file being analyzed for the "Change Signature" action.
   * @return An integer value representing a place to call "Change Signature".
   */
  abstract fun findChangeSignatureOffset(offset: Int, file: PsiFile): Int?

  override fun createCommand(context: CommandCompletionProviderContext): ActionCompletionCommand? {
    return object : ActionCompletionCommand(actionId = super.actionId,
                                            name = super.name,
                                            i18nName = super.i18nName,
                                            icon = super.icon,
                                            priority = super.priority,
                                            previewText = super.previewText) {

      override fun execute(offset: Int, psiFile: PsiFile, editor: Editor?) {
        val targetOffset = findChangeSignatureOffset(offset, psiFile) ?: return
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
}
