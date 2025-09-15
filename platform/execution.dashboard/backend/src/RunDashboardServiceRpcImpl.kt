// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.execution.dashboard.backend

import com.intellij.platform.execution.dashboard.RunDashboardManagerImpl
import com.intellij.platform.execution.dashboard.splitApi.*
import com.intellij.platform.project.ProjectId
import com.intellij.platform.project.findProjectOrNull
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

internal class RunDashboardServiceRpcImpl : RunDashboardServiceRpc {
  override suspend fun getSettings(projectId: ProjectId): Flow<RunDashboardSettingsDto> {
    val project = projectId.findProjectOrNull() ?: return emptyFlow()
    return RunDashboardManagerImpl.getInstance(project).settingsDto
  }

  override suspend fun getServices(projectId: ProjectId): Flow<List<RunDashboardServiceDto>> {
    val project = projectId.findProjectOrNull() ?: return emptyFlow()
    return RunDashboardManagerImpl.getInstance(project).servicesDto
  }

  override suspend fun getStatuses(projectId: ProjectId): Flow<ServiceStatusDto> {
    val project = projectId.findProjectOrNull() ?: return emptyFlow()
    return RunDashboardManagerImpl.getInstance(project).statusesDto
  }

  override suspend fun getCustomizations(projectId: ProjectId): Flow<ServiceCustomizationDto> {
    val project = projectId.findProjectOrNull() ?: return emptyFlow()
    return RunDashboardManagerImpl.getInstance(project).customizationsDto
  }
}