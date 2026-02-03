// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.util

import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.project.Project
import com.intellij.platform.backend.observation.ActivityTracker
import kotlinx.coroutines.delay
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class ExternalSystemTracker : ActivityTracker {
  override val presentableName: String = "external-system (startup)"

  override suspend fun isInProgress(project: Project): Boolean {
    return project.serviceAsync<ExternalSystemInProgressService>().isInProgress()
  }

  override suspend fun awaitConfiguration(project: Project) {
    val isAwaitingActivities = project.serviceAsync<ExternalSystemInProgressService>().isInProgress()
    if (isAwaitingActivities) {
      delay(1000)
    }
  }
}
