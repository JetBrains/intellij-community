// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion.command.commands

import com.intellij.analysis.AnalysisBundle.message
import com.intellij.codeInsight.completion.command.*
import com.intellij.codeInsight.intention.impl.IntentionActionWithTextCaching
import com.intellij.codeInsight.intention.impl.ShowIntentionActionsHandler
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.openapi.application.readAction
import com.intellij.openapi.editor.Editor
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.psi.PsiFile
import org.jetbrains.annotations.Nls
import javax.swing.Icon

internal class IntentionCompletionCommand(
  private val intentionAction: IntentionActionWithTextCaching,
  override val priority: Int?,
  override val icon: Icon?,
  override val highlightInfo: HighlightInfoLookup?,
  private val myOffset: Int,
  private val previewProvider: () -> IntentionPreviewInfo?,
) : CompletionCommand(), CompletionCommandWithPreview {

  override val name: String
    get() = intentionAction.text

  override val i18nName: @Nls String
    get() = intentionAction.text

  override fun execute(offset: Int, psiFile: PsiFile, editor: Editor?) {
    if (editor == null) return
    val marker = editor.document.createRangeMarker(offset, offset)
    val targetMarker = editor.document.createRangeMarker(myOffset, myOffset)
    editor.caretModel.moveToOffset(myOffset)
    val availableFor =
      runWithModalProgressBlocking(psiFile.project, message("scanning.scope.progress.title")) {
        readAction {
          ShowIntentionActionsHandler.availableFor(psiFile, editor, myOffset, intentionAction.action)
        }
      }
    if (!intentionAction.action.startInWriteAction() && availableFor) {
      editor.putUserData(KEY_FORCE_CARET_OFFSET, ForceOffsetData(myOffset, offset))
    }
    if (availableFor) {
      ShowIntentionActionsHandler.chooseActionAndInvoke(psiFile, editor, intentionAction.action, name)
    }
    if (!intentionAction.action.startInWriteAction() || (targetMarker.isValid && targetMarker.startOffset != editor.caretModel.offset)) {
      //probably, intention moves the cursor or async gui
      return
    }
    if (marker.isValid) {
      editor.caretModel.moveToOffset(marker.endOffset)
    }
    else {
      editor.caretModel.moveToOffset(offset)
    }
  }

  override fun getPreview(): IntentionPreviewInfo? {
    return previewProvider.invoke()
  }
}