// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.feedback.general

import com.intellij.feedback.AbstractInIdeGeneralFeedbackProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.platform.feedback.general.dialog.GeneralFeedbackDialog

class InIdeGeneralFeedbackProvider : AbstractInIdeGeneralFeedbackProvider() {
  override fun getGeneralFeedbackDialog(project: Project?, forTest: Boolean): DialogWrapper {
    return GeneralFeedbackDialog(project, forTest)
  }
}