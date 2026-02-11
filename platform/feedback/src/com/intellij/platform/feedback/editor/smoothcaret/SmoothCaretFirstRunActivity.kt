// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.feedback.editor.smoothcaret

import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn

/**
 * Tracks the first time a user runs the EAP by recording the date
 * on the first project open during EAP period.
 */
internal class SmoothCaretFirstRunActivity : ProjectActivity {
  override suspend fun execute(project: Project) {
    // Only track first run during EAP
    if (!ApplicationInfo.getInstance().isEAP) {
      return
    }

    val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
    SmoothCaretUsageLocalStorage.getInstance().recordFirstRunIfNeeded(today.toString()) // ISO-8601: "2026-02-10"
  }
}
