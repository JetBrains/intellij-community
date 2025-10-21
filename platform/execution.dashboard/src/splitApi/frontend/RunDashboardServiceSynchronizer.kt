// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.execution.dashboard.splitApi.frontend

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.platform.execution.dashboard.RunDashboardCoroutineScopeProvider
import com.intellij.platform.execution.serviceView.shouldEnableServicesViewInCurrentEnvironment
import com.intellij.platform.util.coroutines.childScope
import kotlinx.coroutines.launch

private class RunDashboardServiceSynchronizer : ProjectActivity {
  override suspend fun execute(project: Project) {
    if (!shouldEnableServicesViewInCurrentEnvironment()) return

    val synchronizationScope = RunDashboardCoroutineScopeProvider.getInstance(project).cs.childScope("RunDashboardServiceSynchronizer")
    synchronizationScope.launch {
      FrontendRunDashboardManager.getInstance(project).subscribeToBackendSettingsUpdates()
    }
    synchronizationScope.launch {
      FrontendRunDashboardManager.getInstance(project).subscribeToBackendServicesUpdates()
    }
    synchronizationScope.launch {
      FrontendRunDashboardManager.getInstance(project).subscribeToBackendStatusesUpdates()
    }
    synchronizationScope.launch {
      FrontendRunDashboardManager.getInstance(project).subscribeToBackendCustomizationsUpdates()
    }
    synchronizationScope.launch {
      FrontendRunDashboardManager.getInstance(project).subscribeToBackendConfigurationTypesUpdates()
    }
    synchronizationScope.launch {
      FrontendRunDashboardLuxHolder.getInstance(project).subscribeToRunToolwindowUpdates()
    }
    synchronizationScope.launch {
      FrontendRunDashboardManager.getInstance(project).subscribeToBackendAvailableConfigurationUpdates()
    }
    synchronizationScope.launch {
      FrontendRunDashboardManager.getInstance(project).subscribeToBackendExcludedConfigurationUpdates()
    }
  }
}