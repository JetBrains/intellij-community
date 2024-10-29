// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupManager
import com.intellij.platform.backend.observation.ActivityTracker
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class StartupActivityTracker : ActivityTracker {
  override val presentableName: String = "startup-activities"

  override suspend fun isInProgress(project: Project): Boolean {
    return !StartupManager.getInstance(project).postStartupActivityPassed()
  }

  override suspend fun awaitConfiguration(project: Project) {
    StartupManager.getInstance(project).allActivitiesPassedFuture.join()
  }
}