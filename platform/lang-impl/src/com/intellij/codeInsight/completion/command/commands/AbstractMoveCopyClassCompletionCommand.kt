// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion.command.commands

import com.intellij.codeInsight.completion.command.CommandCompletionProviderContext
import com.intellij.idea.ActionsBundle
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiFile

abstract class AbstractMoveCompletionCommandProvider : ActionCommandProvider(
  actionId = "Move",
  name = "Move element",
  i18nName = ActionsBundle.message("action.Move.text"),
  icon = null,
  priority = -100,
  previewText = ActionsBundle.message("action.Move.description"),
) {

  override fun isApplicable(offset: Int, psiFile: PsiFile, editor: Editor?): Boolean {
    val offset = findMoveClassOffset(offset, psiFile) ?: return false
    editor?.caretModel?.moveToOffset(offset)
    return super.isApplicable(offset, psiFile, editor)
  }


  /**
   * Finds and identifies a potential "Move class" action at the specified position
   * within the given file.
   *
   * @param offset The position within the file where the "Move class" action may be applicable.
   * @param file The PsiFile instance representing the file being analyzed for the "Move class" action.
   * @return An integer value representing a place to call "Move class".
   */
  abstract fun findMoveClassOffset(offset: Int, psiFile: PsiFile): Int?

  override fun createCommand(context: CommandCompletionProviderContext): ActionCompletionCommand? {
    return object : ActionCompletionCommand(actionId = super.actionId,
                                            name = super.name,
                                            i18nName = super.i18nName,
                                            icon = super.icon,
                                            priority = super.priority,
                                            previewText = super.previewText) {

      override fun execute(offset: Int, psiFile: PsiFile, editor: Editor?) {
        val targetOffset = findMoveClassOffset(offset, psiFile) ?: return
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

abstract class AbstractCopyClassCompletionCommandProvider : ActionCommandProvider(
  actionId = "CopyElement",
  name = "Copy class",
  i18nName = ActionsBundle.message("action.CopyElement.text"),
  icon = null,
  priority = -100,
  previewText = ActionsBundle.message("action.CopyElement.description"),
) {

  override fun isApplicable(offset: Int, psiFile: PsiFile, editor: Editor?): Boolean {
    val offset = findMoveClassOffset(offset, psiFile) ?: return false
    editor?.caretModel?.moveToOffset(offset)
    return super.isApplicable(offset, psiFile, editor)
  }


  /**
   * Finds and identifies a potential "Move class" action at the specified position
   * within the given file.
   *
   * @param offset The position within the file where the "Move class" action may be applicable.
   * @param psiFile The PsiFile instance representing the file being analyzed for the "Move class" action.
   * @return An integer value representing a place to call "Move class".
   */
  abstract fun findMoveClassOffset(offset: Int, psiFile: PsiFile): Int?

  override fun createCommand(context: CommandCompletionProviderContext): ActionCompletionCommand? {
    return object : ActionCompletionCommand(actionId = super.actionId,
                                            name = super.name,
                                            i18nName = super.i18nName,
                                            icon = super.icon,
                                            priority = super.priority,
                                            previewText = super.previewText) {

      override fun execute(offset: Int, psiFile: PsiFile, editor: Editor?) {
        val targetOffset = findMoveClassOffset(offset, psiFile) ?: return
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
