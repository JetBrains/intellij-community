// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui.localization.statistics

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

internal class StartupStatisticsListener : ProjectActivity {
  override suspend fun execute(project: Project) {
    unSentEvents.forEach {
      val event = it.first
      val params = it.second
      event.log(params)
    }
    unSentEvents.clear()
  }
}