// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.execution.dashboard

import com.intellij.execution.RunContentDescriptorIdImpl
import com.intellij.execution.RunManager
import com.intellij.execution.RunManagerEx
import com.intellij.execution.dashboard.RunDashboardCustomizer
import com.intellij.execution.dashboard.RunDashboardRunConfigurationStatus
import com.intellij.execution.dashboard.RunDashboardService
import com.intellij.execution.dashboard.RunDashboardServiceId
import com.intellij.ide.ui.icons.rpcId
import com.intellij.openapi.project.Project
import com.intellij.platform.execution.dashboard.splitApi.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*

internal class BackendRunDashboardManagerState(private val project: Project) {
  private val sharedSettings = MutableStateFlow(RunDashboardSettingsDto())
  private val sharedServicesState = MutableStateFlow<List<RunDashboardServiceDto>>(emptyList())

  // Extracted into a separate flow because it has a lot of updates and a few active nodes -> traffic economy.
  // The other flow here, on the contrary, has a few updates and a lot of active nodes.
  private val sharedServicesCustomizations = MutableSharedFlow<ServiceCustomizationDto>(1, 100, BufferOverflow.DROP_OLDEST)
  private val tagCallbacksByServiceId = HashMap<RunDashboardServiceId, List<CustomLinkDto>>()

  private val sharedStatuses = MutableSharedFlow<ServiceStatusDto>(1, 100, BufferOverflow.DROP_OLDEST)

  private val sharedExcludedTypes = MutableStateFlow(emptySet<String>())

  fun getLinkByServiceId(link: String, serviceId: RunDashboardServiceId): Runnable? {
    return tagCallbacksByServiceId[serviceId]?.firstOrNull { linkDto -> linkDto.presentableText == link }?.callback
  }

  fun getSettings(): Flow<RunDashboardSettingsDto> {
    return sharedSettings.asStateFlow()
  }

  fun getServices(): Flow<List<RunDashboardServiceDto>> {
    return sharedServicesState.asStateFlow()
  }

  fun setServices(value: List<List<RunDashboardService>>) {
    val flattenServices = value.flatten()
    sharedServicesState.value = flattenServices.map { backendServiceModel ->
      createServiceDto(backendServiceModel)
    }

    val effectiveServicesSet = flattenServices.asSequence().map { it.uuid }.toSet()
    tagCallbacksByServiceId.keys.retainAll(effectiveServicesSet)
  }

  fun setSettings(openRunningConfigInTab: Boolean) {
    sharedSettings.value = RunDashboardSettingsDto(openRunningConfigInTab)
  }

  fun fireExcludedTypesUpdated(excludedTypes: Set<String>) {
    sharedExcludedTypes.value = excludedTypes
  }

  fun getExcludedTypes(): Flow<Set<String>> {
    return sharedExcludedTypes.asStateFlow()
  }

  fun fireStatusUpdated(backendService: RunDashboardService, persistedStatus: RunDashboardRunConfigurationStatus?) {
    val effectiveStatus = when {
      backendService.descriptor == null && persistedStatus != null -> persistedStatus
      else -> RunDashboardRunConfigurationStatus.getStatus(backendService.descriptor)
    }
    sharedStatuses.tryEmit(ServiceStatusDto(backendService.uuid, effectiveStatus.id))
  }

  fun getStatuses(): Flow<ServiceStatusDto> {
    return sharedStatuses.asSharedFlow()
  }

  fun fireCustomizationUpdated(backendService: RunDashboardService, customizers: List<RunDashboardCustomizer>) {
    val value = createCustomizationDto(backendService, customizers)
    tagCallbacksByServiceId[backendService.uuid] = value.links
    sharedServicesCustomizations.tryEmit(value)
  }

  fun getCustomizations(): Flow<ServiceCustomizationDto> {
    return sharedServicesCustomizations.asSharedFlow()
  }

  private fun createCustomizationDto(backendService: RunDashboardService, customizers: List<RunDashboardCustomizer>): ServiceCustomizationDto {
    val customizationBuilder = RunDashboardCustomizationBuilderImpl()
    for (customizer in customizers) {
      if (customizer.updatePresentation(customizationBuilder, backendService.configurationSettings, backendService.descriptor)) {
        break
      }
    }

    return customizationBuilder.buildDto(backendService.uuid)
  }

  companion object {
    @JvmStatic
    fun createServiceDto(backendServiceModel: RunDashboardService): RunDashboardServiceDto {
      val settings = backendServiceModel.configurationSettings
      val configuration = settings.configuration
      val project = configuration.project
      val contentIdImpl = backendServiceModel.descriptor?.id as? RunContentDescriptorIdImpl

      if (backendServiceModel is RunDashboardManagerImpl.RunDashboardServiceImpl) {
        return RunDashboardMainServiceDto(
          uuid = backendServiceModel.uuid,
          name = configuration.name,
          iconId = RunManagerEx.getInstanceEx(project).getConfigurationIcon(settings)?.rpcId(),
          typeId = configuration.type.id,
          typeDisplayName = configuration.type.displayName,
          typeIconId = configuration.type.icon.rpcId(),
          folderName = settings.folderName,
          contentId = contentIdImpl,
          isRemovable = RunManager.getInstance(project).hasSettings(settings),
          serviceViewId = backendServiceModel.serviceViewId,
          isStored = RunManager.getInstance(project).hasSettings(settings),
          isActivateToolWindowBeforeRun = settings.isActivateToolWindowBeforeRun,
          isFocusToolWindowBeforeRun = settings.isFocusToolWindowBeforeRun
        )
      }
      else {
        return RunDashboardAdditionalServiceDto(
          uuid = backendServiceModel.uuid,
          name = configuration.name,
          iconId = RunManagerEx.getInstanceEx(project).getConfigurationIcon(settings)?.rpcId(),
          typeId = configuration.type.id,
          typeDisplayName = configuration.type.displayName,
          typeIconId = configuration.type.icon.rpcId(),
          folderName = settings.folderName,
          contentId = contentIdImpl,
          isRemovable = RunManager.getInstance(project).hasSettings(settings),
          serviceViewId = backendServiceModel.serviceViewId,
          isStored = RunManager.getInstance(project).hasSettings(settings),
          isActivateToolWindowBeforeRun = settings.isActivateToolWindowBeforeRun,
          isFocusToolWindowBeforeRun = settings.isFocusToolWindowBeforeRun
        )
      }
    }
  }
}