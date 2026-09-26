// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.feedback

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent

class KotlinDebuggerFeedbackTestAction : AnAction(
  KotlinDebuggerFeedbackSurveyBundle.message("kotlin.debugger.feedback.notification.test.name")
) {

  override fun actionPerformed(e: AnActionEvent) {
    KotlinDebuggerSurveyFeedbackDialog(e.project, true).show()
  }
}