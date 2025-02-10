// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.feedback.csat

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent

internal class CsatFeedbackAction : AnAction() {
  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project
    if (project != null) {
      CsatFeedbackSurvey().showNotification(project, false)
    }
  }
}