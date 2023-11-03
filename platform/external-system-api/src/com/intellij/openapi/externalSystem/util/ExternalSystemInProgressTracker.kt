// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.util

import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.project.Project
import com.intellij.platform.backend.observation.ActivityInProgressTracker
import com.intellij.platform.backend.observation.ActivityKey
import kotlinx.coroutines.delay
import org.jetbrains.annotations.Nls


class ExternalSystemInProgressTracker : ActivityInProgressTracker {
  override val presentableName: String = "external-system (startup)"

  override suspend fun isInProgress(project: Project): Boolean {
    return project.serviceAsync<ExternalSystemInProgressService>().isInProgress()
  }

  override suspend fun awaitConfiguration(project: Project) {
    val isAwaitingActivities = project.serviceAsync<ExternalSystemInProgressService>().isInProgress()
    if (isAwaitingActivities) {
      delay(100)
    }
  }
}

object ExternalSystemActivityKey : ActivityKey {
  override val presentableName: @Nls String
    get() = "external-system"
}