// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.feedback.evaluation

import com.intellij.feedback.InIdeEvaluationFeedbackProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.platform.feedback.evaluation.dialog.EvaluationFeedbackDialog

class InIdeEvaluationFeedbackProviderImpl : InIdeEvaluationFeedbackProvider() {
  override fun getEvaluationFeedbackDialog(project: Project?): DialogWrapper {
    return EvaluationFeedbackDialog(project, false)
  }
}