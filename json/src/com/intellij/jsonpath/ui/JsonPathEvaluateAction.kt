// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.jsonpath.ui

import com.intellij.json.JsonUtil
import com.intellij.json.psi.JsonFile
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.psi.PsiDocumentManager

internal class JsonPathEvaluateAction : DumbAwareAction() {

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val editor = e.getData(CommonDataKeys.EDITOR)

    if (editor != null) {
      val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.document)
      if (psiFile is JsonFile) {
        JsonPathEvaluateManager.getInstance(project).evaluateFromJson(psiFile)
        return
      }
    }

    JsonPathEvaluateManager.getInstance(project).evaluateExpression()
  }

  override fun update(e: AnActionEvent) {
    if (e.place == ActionPlaces.EDITOR_POPUP) {
      val editor = e.getData(CommonDataKeys.EDITOR)
      val file = editor?.let { FileDocumentManager.getInstance().getFile(editor.document) }
      e.presentation.isEnabledAndVisible = file != null && JsonUtil.isJsonFile(file, editor.project)
    } else {
      e.presentation.isEnabledAndVisible = e.project != null
    }
  }
}