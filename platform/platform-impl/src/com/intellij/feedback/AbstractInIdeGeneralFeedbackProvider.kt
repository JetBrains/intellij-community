// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.feedback

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper

open class AbstractInIdeGeneralFeedbackProvider {

  companion object {
    @JvmStatic
    fun getInstance(): AbstractInIdeGeneralFeedbackProvider = service()
  }

  open fun getGeneralFeedbackDialog(project: Project?, forTest: Boolean): DialogWrapper {
    throw UnsupportedOperationException("The method must be overridden")
  }
}