// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.feedback.demo.action

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.platform.feedback.demo.DemoInIdeFeedbackSurvey
import com.intellij.platform.feedback.demo.bundle.DemoFeedbackBundle

class ShowInIdeDemoFeedbackWithStatsAction : AnAction(DemoFeedbackBundle.message("show.inIde.demo.survey.action.name")) {
  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project
    if (project != null) {
      DemoInIdeFeedbackSurvey().showNotification(project, false)
    }
  }
}