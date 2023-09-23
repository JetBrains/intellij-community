// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.service

import com.intellij.ide.observation.ActivityInProgressPredicate
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.externalSystem.util.ExternalSystemInProgressService
import com.intellij.openapi.project.Project


class ExternalSystemInProgressPredicate : ActivityInProgressPredicate {
  override val presentableName: String = "external-system"

  override suspend fun isInProgress(project: Project): Boolean {
    return project.serviceAsync<ExternalSystemInProgressService>().isInProgress()
  }
}