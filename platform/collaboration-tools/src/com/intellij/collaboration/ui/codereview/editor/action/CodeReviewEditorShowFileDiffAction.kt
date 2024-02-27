// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.codereview.editor.action

import com.intellij.collaboration.messages.CollaborationToolsBundle
import com.intellij.collaboration.ui.codereview.editor.CodeReviewEditorGutterActionableChangesModel
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.DumbAwareAction

class CodeReviewEditorShowFileDiffAction : DumbAwareAction(CollaborationToolsBundle.messagePointer("review.diff.action.show.text")) {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

  override fun update(e: AnActionEvent) {
    val model = e.getData(CommonDataKeys.EDITOR)?.getUserData(CodeReviewEditorGutterActionableChangesModel.KEY)
    with(e.presentation) {
      text = CollaborationToolsBundle.message("review.diff.action.show.text")
      description = CollaborationToolsBundle.message("review.diff.action.show.description")
      isEnabledAndVisible = model != null
    }
  }

  override fun actionPerformed(e: AnActionEvent) {
    val editor = e.getData(CommonDataKeys.EDITOR) ?: return
    val model = editor.getUserData(CodeReviewEditorGutterActionableChangesModel.KEY) ?: return
    val line = e.getData(CommonDataKeys.CARET)?.logicalPosition?.line
    model.showDiff(line)
  }
}