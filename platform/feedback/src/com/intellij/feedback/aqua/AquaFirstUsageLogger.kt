// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.feedback.aqua

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

internal class AquaFirstUsageLogger : ProjectActivity {
  override suspend fun execute(project: Project) {
    ApplicationManager.getApplication().service<AquaFeedbackSurveyTriggers>().captureFirstUsageTime()
  }
}