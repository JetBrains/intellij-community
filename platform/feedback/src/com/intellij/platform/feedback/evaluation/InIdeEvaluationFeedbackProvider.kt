// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.feedback.evaluation

import com.intellij.feedback.AbstractInIdeEvaluationFeedbackProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.platform.feedback.evaluation.dialog.EvaluationFeedbackDialog

class InIdeEvaluationFeedbackProvider : AbstractInIdeEvaluationFeedbackProvider() {
  override fun getEvaluationFeedbackDialog(project: Project?, forTest: Boolean): DialogWrapper {
    return EvaluationFeedbackDialog(project, forTest)
  }
}