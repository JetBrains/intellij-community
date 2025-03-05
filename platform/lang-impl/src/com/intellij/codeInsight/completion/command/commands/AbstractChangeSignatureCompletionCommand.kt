// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion.command.commands

import com.intellij.idea.ActionsBundle
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiFile

/**
 * Abstract base class for a completion command that integrates with the "Change Signature" action within the IDE.
 */
abstract class AbstractChangeSignatureCompletionCommand :
  AbstractActionCompletionCommand("ChangeSignature",
                                  "Change Signature",
                                  ActionsBundle.message("action.ChangeSignature.text"),
                                  null) {
  final override fun supportsReadOnly(): Boolean = false

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
