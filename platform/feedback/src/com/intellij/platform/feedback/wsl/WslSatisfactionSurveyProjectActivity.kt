// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.feedback.wsl

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

/**
 * Records that a WSL project was opened, feeding the "days working with WSL" counter used by
 * [WslSatisfactionSurveyConfig] to decide when to show the WSL satisfaction survey.
 */
internal class WslSatisfactionSurveyProjectActivity : ProjectActivity {
  override suspend fun execute(project: Project) {
    if (!project.isWslProject()) return
    WslSatisfactionSurveyStore.getInstance().recordWslProjectOpened()
  }
}
