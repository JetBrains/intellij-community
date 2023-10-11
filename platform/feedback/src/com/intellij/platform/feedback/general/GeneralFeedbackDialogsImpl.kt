// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.feedback.general

import com.intellij.feedback.GeneralFeedbackDialogs
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.platform.feedback.general.evaluation.EvaluationFeedbackDialog
import com.intellij.platform.feedback.general.general.GeneralFeedbackDialog

class GeneralFeedbackDialogsImpl : GeneralFeedbackDialogs() {

  override fun getGeneralFeedbackDialog(project: Project?): DialogWrapper {
    return GeneralFeedbackDialog(project, false)
  }

  override fun getEvaluationFeedbackDialog(project: Project?): DialogWrapper {
    return EvaluationFeedbackDialog(project, false)
  }
}