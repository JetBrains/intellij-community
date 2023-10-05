// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.feedback

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper

open class AbstractInIdeEvaluationFeedbackProvider {

  companion object {
    @JvmStatic
    fun getInstance(): AbstractInIdeEvaluationFeedbackProvider = service()
  }

  open fun getEvaluationFeedbackDialog(project: Project?, forTest: Boolean): DialogWrapper {
    throw UnsupportedOperationException("The method must be overridden")
  }
}