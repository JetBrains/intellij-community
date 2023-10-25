// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.util

import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.project.Project
import com.intellij.platform.backend.observation.MarkupBasedActivityInProgressWitness
import kotlinx.coroutines.delay


class ExternalSystemInProgressWitness : MarkupBasedActivityInProgressWitness() {
  override val presentableName: String = "external-system"

  override suspend fun isInProgress(project: Project): Boolean {
    return project.serviceAsync<ExternalSystemInProgressService>().isInProgress() || super.isInProgress(project)
  }

  override suspend fun awaitConfiguration(project: Project) {
    val isAwaitingActivities = project.serviceAsync<ExternalSystemInProgressService>().isInProgress()
    if (isAwaitingActivities) {
      delay(100)
    }
    return super.awaitConfiguration(project)
  }
}