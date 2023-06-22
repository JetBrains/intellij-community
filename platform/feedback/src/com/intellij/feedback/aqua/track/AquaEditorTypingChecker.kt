// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.feedback.aqua.track

import com.intellij.codeInsight.editorActions.TypedHandlerDelegate
import com.intellij.feedback.aqua.state.AquaNewUserFeedbackService
import com.intellij.feedback.aqua.state.AquaOldUserFeedbackService
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import java.util.concurrent.atomic.AtomicBoolean

class AquaEditorTypingChecker : TypedHandlerDelegate() {
  private val typed = AtomicBoolean(false)
  override fun charTyped(c: Char, project: Project, editor: Editor, file: PsiFile): Result {
    // Ensures the AquaFeedbackSurveyTriggers is requested only once
    if (typed.compareAndSet(false, true)) {
      ApplicationManager.getApplication().service<AquaNewUserFeedbackService>().state.userTypedInEditor = true
      ApplicationManager.getApplication().service<AquaOldUserFeedbackService>().state.userTypedInEditor = true
    }

    return Result.CONTINUE
  }
}