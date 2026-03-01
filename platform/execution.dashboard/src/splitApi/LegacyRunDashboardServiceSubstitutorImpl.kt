// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.execution.dashboard.splitApi

import com.intellij.execution.dashboard.LegacyRunDashboardServiceSubstitutor
import com.intellij.execution.dashboard.RunDashboardRunConfigurationNode
import com.intellij.openapi.project.Project
import com.intellij.platform.execution.dashboard.RunDashboardManagerImpl
import com.intellij.platform.execution.dashboard.splitApi.frontend.tree.FrontendRunConfigurationNode

internal class LegacyRunDashboardServiceSubstitutorImpl : LegacyRunDashboardServiceSubstitutor {
  override fun substituteWithBackendService(maybeFrontendConfigurationNode: RunDashboardRunConfigurationNode, project: Project): RunDashboardRunConfigurationNode {
    if (maybeFrontendConfigurationNode is FrontendRunConfigurationNode) {
      val backendCounterpart = RunDashboardManagerImpl.getInstance(project).findServiceById(maybeFrontendConfigurationNode.service.uuid)
      return backendCounterpart ?: maybeFrontendConfigurationNode
    }
    return maybeFrontendConfigurationNode
  }
}