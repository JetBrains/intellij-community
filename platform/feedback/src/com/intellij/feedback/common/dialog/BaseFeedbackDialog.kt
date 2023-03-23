// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.feedback.common.dialog

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper

abstract class BaseFeedbackDialog(project: Project?) : DialogWrapper(project) {

  protected abstract val feedbackJsonVersion: Int
  protected abstract val feedbackReportId: String

  init {
    isResizable = false
  }

}