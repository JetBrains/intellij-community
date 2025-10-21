// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion.command.commands

import com.intellij.codeInsight.actions.MultiCaretCodeInsightActionHandler
import com.intellij.codeInsight.completion.command.CommandCompletionProviderContext
import com.intellij.codeInsight.completion.command.MyEditor
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.codeInsight.intention.preview.IntentionPreviewUtils
import com.intellij.model.SideEffectGuard
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.SelectionModel
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.source.PostprocessReformattingAspect
import org.jetbrains.annotations.ApiStatus


@ApiStatus.Internal
fun tryToCalculateCommandCompletionPreview(
  previewGenerator: (Editor, PsiFile, Int) -> IntentionPreviewInfo?,
  context: CommandCompletionProviderContext,
  highlight: (Int, PsiFile, SelectionModel) -> Boolean,
  fallback: () -> IntentionPreviewInfo,
): IntentionPreviewInfo {
  try {
    val copiedFile = (context.psiFile.copy() as? PsiFile) ?: return fallback()
    val customEditor = createCustomEditor(copiedFile, context.editor, context.offset)

    var preview: IntentionPreviewInfo? = null
    IntentionPreviewUtils.previewSession(customEditor) {
      PostprocessReformattingAspect.getInstance(context.project).postponeFormattingInside {
        preview = SideEffectGuard.computeWithoutSideEffects {
          if (!highlight(context.offset, copiedFile, customEditor.selectionModel)) return@computeWithoutSideEffects null
          previewGenerator(customEditor, copiedFile, context.offset) ?: fallback()
        }
      }
    }
    return preview ?: fallback()
  }
  catch (e: Exception) {
    return fallback()
  }
}

internal fun tryToCalculateCommandCompletionPreview(
  commentHandler: MultiCaretCodeInsightActionHandler,
  context: CommandCompletionProviderContext,
  highlight: (Int, PsiFile, SelectionModel) -> Boolean,
  fallback: () -> IntentionPreviewInfo,
): IntentionPreviewInfo {
  return tryToCalculateCommandCompletionPreview({ editor, psiFile, offset ->
                                 commentHandler.invoke(context.project, editor, editor.caretModel.currentCaret, psiFile)
                                 commentHandler.postInvoke()
                                 PsiDocumentManager.getInstance(context.project).commitDocument(psiFile.fileDocument)
                                 IntentionPreviewInfo.CustomDiff(context.psiFile.fileType, null, context.psiFile.text, psiFile.text, true)
                               }, context, highlight, fallback)
}

internal fun createCustomEditor(
  psiFile: PsiFile,
  editor: Editor,
  offset: Int,
): MyEditor {
  val intentionEditor = MyEditor(psiFile, editor.settings)
  intentionEditor.caretModel.moveToOffset(offset)
  return intentionEditor
}