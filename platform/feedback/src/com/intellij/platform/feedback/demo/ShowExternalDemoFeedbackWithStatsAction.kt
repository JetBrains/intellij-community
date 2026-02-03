// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.feedback.demo

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent

internal class ShowExternalDemoFeedbackWithStatsAction : AnAction(DemoFeedbackBundle.message("show.external.demo.survey.action.name")) {
  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project
    if (project != null) {
      DemoExternalFeedbackSurvey().showNotification(project, false)
    }
  }
}