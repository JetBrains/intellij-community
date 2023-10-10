// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.feedback.general

import com.intellij.feedback.InIdeGeneralFeedbackProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.platform.feedback.general.dialog.GeneralFeedbackDialog

class InIdeGeneralFeedbackProviderImpl : InIdeGeneralFeedbackProvider() {
  override fun getGeneralFeedbackDialog(project: Project?): DialogWrapper {
    return GeneralFeedbackDialog(project, false)
  }
}