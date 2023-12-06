// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.feedback.aqua.track

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.platform.feedback.aqua.state.AquaOldUserFeedbackService
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

internal class AquaFirstUsageLogger : ProjectActivity {
  override suspend fun execute(project: Project) {
    val oldUserInfoState = AquaOldUserFeedbackService.getInstance().state
    if (oldUserInfoState.firstUsageTime == null) {
      oldUserInfoState.firstUsageTime = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
    }
  }
}