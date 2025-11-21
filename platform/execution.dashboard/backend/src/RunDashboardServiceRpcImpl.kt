// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.execution.dashboard.backend

import com.intellij.execution.ExecutionBundle
import com.intellij.execution.RunManager
import com.intellij.execution.dashboard.RunDashboardManager
import com.intellij.execution.dashboard.RunDashboardServiceId
import com.intellij.execution.impl.RunDialog
import com.intellij.execution.impl.RunManagerImpl
import com.intellij.ide.rpc.ComponentDirectTransferId
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.writeIntentReadAction
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.diagnostic.trace
import com.intellij.platform.execution.dashboard.BackendLuxedRunDashboardContentManager
import com.intellij.platform.execution.dashboard.RunDashboardCoroutineScopeProvider
import com.intellij.platform.execution.dashboard.RunDashboardManagerImpl
import com.intellij.platform.execution.dashboard.splitApi.*
import com.intellij.platform.project.ProjectId
import com.intellij.platform.project.findProjectOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withContext

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

  override suspend fun getConfigurationTypes(projectId: ProjectId): Flow<Set<String>> {
    val project = projectId.findProjectOrNull() ?: return emptyFlow()
    return RunDashboardManagerImpl.getInstance(project).configurationTypes;
  }

  override suspend fun updateConfigurationFolderName(projectId: ProjectId, serviceIds: List<RunDashboardServiceId>, newGroupName: String?) {
    val project = projectId.findProjectOrNull() ?: return
    val dashboardManagerImpl = RunDashboardManagerImpl.getInstance(project)
    val backendServices = serviceIds.mapNotNull { dashboardManagerImpl.findServiceById(it) }

    val runManager = RunManagerImpl.getInstanceImpl(project)
    runManager.fireBeginUpdate()
    try {
      backendServices.forEach { node -> node.getConfigurationSettings().setFolderName(newGroupName) }
    }
    finally {
      runManager.fireEndUpdate()
    }
  }

  override suspend fun getLuxedContentEvents(projectId: ProjectId): Flow<RunDashboardLuxedContentEvent> {
    val project = projectId.findProjectOrNull() ?: return emptyFlow()
    return BackendLuxedRunDashboardContentManager.getInstance(project).getLuxedContents()
  }

  override suspend fun startLuxingContentForService(projectId: ProjectId, id: RunDashboardServiceId): ComponentDirectTransferId? {
    val project = projectId.findProjectOrNull() ?: return null
    return withContext(Dispatchers.EDT) {
      BackendLuxedRunDashboardContentManager.getInstance(project).startLuxing(id)
    }
  }

  override suspend fun pauseLuxingContentForService(projectId: ProjectId, id: RunDashboardServiceId) {
    val project = projectId.findProjectOrNull() ?: return
    withContext(Dispatchers.EDT) {
      BackendLuxedRunDashboardContentManager.getInstance(project).pauseLuxing(id)
    }
  }

  override suspend fun getAvailableConfigurations(projectId: ProjectId): Flow<Set<RunDashboardConfigurationDto>> {
    val project = projectId.findProjectOrNull() ?: return emptyFlow()
    // todo backend state with updates
    val availableConfigurations = RunManager.getInstance(project).allSettings.map { configurationSettings ->
      val configurationId = configurationSettings.configuration.storeGlobally(RunDashboardCoroutineScopeProvider.getInstance(project).cs)
      RunDashboardConfigurationDto(configurationSettings.type.id, configurationSettings.name, configurationSettings.folderName, configurationId)
    }.toSet()
    return flowOf(availableConfigurations)
  }

  override suspend fun getExcludedConfigurations(projectId: ProjectId): Flow<Set<String>> {
    val project = projectId.findProjectOrNull() ?: return emptyFlow()
    return RunDashboardManagerImpl.getInstance(project).excludedTypesDto
  }

  override suspend fun getNavigateToServiceEvents(projectId: ProjectId): Flow<NavigateToServiceEvent> {
    val project = projectId.findProjectOrNull() ?: return emptyFlow()
    return RunDashboardManagerImpl.getInstance(project).navigateToServiceEvents
  }

  override suspend fun setNewExcluded(projectId: ProjectId, configurationTypeId: String, newExcluded: Boolean) {
    val project = projectId.findProjectOrNull() ?: return
    RunDashboardManagerImpl.getInstance(project).setNewExcluded(configurationTypeId, newExcluded)
  }

  override suspend fun restoreConfigurations(projectId: ProjectId, configurations: List<RunDashboardConfigurationId>) {
    val project = projectId.findProjectOrNull() ?: return
    val backendConfigurations = configurations.map { it.findConfigurationValue() }
    thisLogger().trace {
      "Received ${configurations.size} configurations from frontend, resolved ${backendConfigurations.size}. " +
      "Namely, from frontend: $RunDashboardConfigurationId" +
      "and resolved: $backendConfigurations"
    }
    RunDashboardManagerImpl.getInstance(project).restoreConfigurations(backendConfigurations)
  }

  override suspend fun hideConfigurations(projectId: ProjectId, configurations: List<RunDashboardConfigurationId>) {
    val project = projectId.findProjectOrNull() ?: return
    val backendConfigurations = configurations.map { it.findConfigurationValue() }
    thisLogger().trace {
      "Received ${configurations.size} configurations from frontend, resolved ${backendConfigurations.size}. " +
      "Namely, from frontend: $RunDashboardConfigurationId" +
      "and resolved: $backendConfigurations"
    }
    RunDashboardManagerImpl.getInstance(project).hideConfigurations(backendConfigurations)
  }

  override suspend fun getRunManagerUpdates(projectId: ProjectId): Flow<Unit> {
    val project = projectId.findProjectOrNull() ?: return emptyFlow()
    return RunDashboardConfigurationUpdatesHolder.getInstance(project).getUpdates()
  }

  override suspend fun editConfiguration(projectId: ProjectId, serviceId: RunDashboardServiceId) {
    val project = projectId.findProjectOrNull() ?: return
    val backendConfigurationSettings =
      RunDashboardManagerImpl.getInstance(project).findServiceById(serviceId)?.configurationSettings ?: return
    withContext(Dispatchers.EDT) {
      writeIntentReadAction {
        RunDialog.editConfiguration(project,
                                    backendConfigurationSettings,
                                    ExecutionBundle.message("run.dashboard.edit.configuration.dialog.title"))
      }
    }
  }

  override suspend fun copyConfiguration(projectId: ProjectId, serviceId: RunDashboardServiceId) {
    val project = projectId.findProjectOrNull() ?: return
    val backendService = RunDashboardManagerImpl.getInstance(project).findServiceById(serviceId) ?: return
    withContext(Dispatchers.EDT) {
      RunDashboardEditConfigurationUtils.editConfiguration(project, backendService)
    }
  }

  override suspend fun hideConfiguration(projectId: ProjectId, serviceIds: List<RunDashboardServiceId>) {
    val project = projectId.findProjectOrNull() ?: return
    val configurations = serviceIds.mapNotNull { serviceId -> RunDashboardManagerImpl.getInstance(project).findServiceById(serviceId)?.configurationSettings?.configuration }
    RunDashboardManager.getInstance(project).hideConfigurations(configurations)
  }
}