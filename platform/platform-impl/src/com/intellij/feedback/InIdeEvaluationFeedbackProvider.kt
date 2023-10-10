// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.feedback

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper

/**
 * This class provides feedback dialogs for evaluation feedback.
 *
 * This class acts like an interface and overridden in `intellij.platform.feedback` module.
 * It helps to get around the problem of circular dependency between current module and `intellij.platform.feedback` module.
 *
 * @see com.intellij.platform.feedback.evaluation.InIdeEvaluationFeedbackProviderImpl
 */
open class InIdeEvaluationFeedbackProvider {

  companion object {
    @JvmStatic
    fun getInstance(): InIdeEvaluationFeedbackProvider = service()
  }

  open fun getEvaluationFeedbackDialog(project: Project?): DialogWrapper? {
    return null
  }
}