package com.intellij.codeInsight.codeVision

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformDataKeys

class ShowCodeVisionPopupAction : AnAction(CodeVisionBundle.message("ShowCodeVisionPopupAction.action.show.code.vision.text")) {
  override fun update(e: AnActionEvent) {
    e.presentation.isEnabled = e.getData(PlatformDataKeys.EDITOR) != null
  }

  override fun actionPerformed(e: AnActionEvent) {
    val editor = e.getData(PlatformDataKeys.EDITOR) ?: return
    val caretOffset = editor.caretModel.offset
    editor.lensContext?.invokeMoreMenu(caretOffset)
  }
}