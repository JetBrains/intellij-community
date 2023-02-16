// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.feedback.localization.dialog

import com.intellij.feedback.common.dialog.BaseFeedbackDialog
import com.intellij.openapi.project.Project
import javax.swing.JComponent

class LocalizationFeedbackDialog(
  private val project: Project?,
  private val forTest: Boolean
) : BaseFeedbackDialog(project) {
  override val feedbackJsonVersion: Int
    get() = TODO("Not yet implemented")
  override val feedbackReportId: String
    get() = TODO("Not yet implemented")
  override val feedbackPrivacyConsentType: String
    get() = TODO("Not yet implemented")

  override fun createCenterPanel(): JComponent? {
    TODO("Not yet implemented")
  }
}