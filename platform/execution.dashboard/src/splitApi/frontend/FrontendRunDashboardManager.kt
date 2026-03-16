// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.execution.dashboard.splitApi.frontend

import com.intellij.execution.RunContentDescriptorId
import com.intellij.execution.RunContentDescriptorIdImpl
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.dashboard.RunDashboardManager
import com.intellij.execution.dashboard.RunDashboardServiceId
import com.intellij.execution.services.ServiceEventListener
import com.intellij.execution.services.ServiceViewManager
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.execution.ui.RunContentManager
import com.intellij.execution.ui.RunContentManagerImpl
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.platform.execution.dashboard.RunDashboardCoroutineScopeProvider
import com.intellij.platform.execution.dashboard.RunDashboardServiceViewContributor
import com.intellij.platform.execution.dashboard.RunDashboardServiceViewContributorHelper
import com.intellij.platform.execution.dashboard.splitApi.RunDashboardAdditionalServiceDto
import com.intellij.platform.execution.dashboard.splitApi.RunDashboardConfigurationDto
import com.intellij.platform.execution.dashboard.splitApi.RunDashboardMainServiceDto
import com.intellij.platform.execution.dashboard.splitApi.RunDashboardServiceDto
import com.intellij.platform.execution.dashboard.splitApi.RunDashboardServiceRpc
import com.intellij.platform.execution.dashboard.splitApi.RunDashboardSettingsDto
import com.intellij.platform.execution.dashboard.splitApi.ServiceCustomizationDto
import com.intellij.platform.execution.dashboard.splitApi.ServiceStatusDto
import com.intellij.platform.execution.dashboard.splitApi.frontend.tree.FrontendRunConfigurationNode
import com.intellij.platform.execution.dashboard.splitApi.frontend.tree.RunDashboardStatusFilter
import com.intellij.platform.execution.dashboard.splitApi.toAdditionalServiceDto
import com.intellij.platform.execution.serviceView.ServiceViewManagerImpl
import com.intellij.platform.execution.serviceView.shouldEnableServicesViewInCurrentEnvironment
import com.intellij.platform.project.projectId
import com.intellij.platform.util.coroutines.childScope
import com.intellij.ui.content.Content
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Service(Service.Level.PROJECT)
internal class FrontendRunDashboardManager(private val project: Project) : RunDashboardManager {
  private val frontendSettings = MutableStateFlow(RunDashboardSettingsDto())
  private val frontendDtos = MutableStateFlow<List<RunDashboardServiceDto>>(emptyList())
  private val frontendStatuses = MutableStateFlow(emptyMap<RunDashboardServiceId, ServiceStatusDto>())
  private val frontendCustomizations = MutableStateFlow(emptyMap<RunDashboardServiceId, ServiceCustomizationDto>())
  private val frontendAvailableConfigurations = MutableStateFlow(emptySet<RunDashboardConfigurationDto>())
  private val frontendExcludedConfigurationTypeIds = MutableStateFlow(emptySet<String>())
  private val statusFilter = RunDashboardStatusFilter()
  private val configurationTypes = MutableStateFlow(emptySet<String>())
  private var isInitialized = MutableStateFlow(false)

  override fun isInitialized(): Boolean {
    return isInitialized.value
  }

  fun tryStartInitialization() {
    if (!shouldEnableServicesViewInCurrentEnvironment()) return
    if (!isInitialized.compareAndSet(expect = false, update = true)) return

    try {
      scheduleFetchInitialState(project)
    }
    finally {
      isInitialized.value = true
    }
  }

  internal suspend fun subscribeToBackendSettingsUpdates() {
    RunDashboardServiceRpc.getInstance().getSettings(project.projectId()).collect { updatesFromBackend ->
      frontendSettings.value = updatesFromBackend
    }
  }

  internal suspend fun subscribeToBackendServicesUpdates() {
    RunDashboardServiceRpc.getInstance().getServices(project.projectId()).collect { updatesFromBackend ->
      frontendDtos.value = updatesFromBackend

      updateDashboard(true)
    }
  }

  internal suspend fun subscribeToBackendStatusesUpdates() {
    RunDashboardServiceRpc.getInstance().getStatuses(project.projectId()).collect { updateFromBackend ->
      val existingStatuses = frontendStatuses.value
      val updatedStatuses = existingStatuses.toMutableMap()
      updatedStatuses[updateFromBackend.id] = updateFromBackend
      frontendStatuses.value = updatedStatuses

      updateDashboard(false)
    }
  }

  internal suspend fun subscribeToBackendCustomizationsUpdates() {
    RunDashboardServiceRpc.getInstance().getCustomizations(project.projectId()).collect { updateFromBackend ->
      val existingCustomizations = frontendCustomizations.value
      val updatedCustomizations = existingCustomizations.toMutableMap()
      updatedCustomizations[updateFromBackend.id] = updateFromBackend
      frontendCustomizations.value = updatedCustomizations

      updateDashboard(false)
    }
  }

  internal suspend fun subscribeToBackendAvailableConfigurationUpdates() {
    RunDashboardServiceRpc.getInstance().getAvailableConfigurations(project.projectId()).collect { updateFromBackend ->
      frontendAvailableConfigurations.value = updateFromBackend
    }
  }

  internal suspend fun subscribeToBackendExcludedConfigurationUpdates() {
    RunDashboardServiceRpc.getInstance().getExcludedConfigurations(project.projectId()).collect { updateFromBackend ->
      frontendExcludedConfigurationTypeIds.value = updateFromBackend
    }
  }

  fun getAvailableConfigurations(): Set<RunDashboardConfigurationDto> {
    return frontendAvailableConfigurations.value
  }

  fun getServices(): List<RunDashboardServiceDto> {
    return frontendDtos.value
  }

  internal suspend fun subscribeToBackendConfigurationTypesUpdates() {
    RunDashboardServiceRpc.getInstance().getConfigurationTypes(project.projectId()).collect { updateFromBackend ->
      configurationTypes.value = updateFromBackend

      updateDashboard(true)
      withContext(Dispatchers.EDT) {
        if (RunDashboardUiManagerImpl.getInstance(project).syncContentsFromBackend()) {
          updateDashboard(true)
        }
      }
    }
  }

  internal suspend fun subscribeToNavigateToServiceEvents() {
    RunDashboardServiceRpc.getInstance().getNavigateToServiceEvents(project.projectId()).collect { updateFromBackend ->
      val serviceDto = frontendDtos.value.find { it.uuid == updateFromBackend.serviceId } ?: return@collect
      val configurationNode = FrontendRunConfigurationNode(project, FrontendRunDashboardService(serviceDto))
      withContext(Dispatchers.EDT) {
        (ServiceViewManager.getInstance(project) as ServiceViewManagerImpl?)
          ?.trackingSelect(configurationNode, RunDashboardServiceViewContributor::class.java,
                           serviceDto.isActivateToolWindowBeforeRun, updateFromBackend.focus)
      }
    }
  }

  fun getServicePresentations(): List<FrontendRunDashboardService> {
    return frontendDtos.value.map { dto -> FrontendRunDashboardService(dto) }
  }

  fun getServiceRunContentDescriptor(service: FrontendRunDashboardService): RunContentDescriptor? {
    val contentId = service.runDashboardServiceDto.contentId ?: return null

    return RunContentManager.getInstance(project).runContentDescriptors.find { it.id == contentId }
  }

  fun getStatusById(id: RunDashboardServiceId): String? {
    return frontendStatuses.value[id]?.statusId
  }

  fun getCustomizationById(id: RunDashboardServiceId): ServiceCustomizationDto? {
    return frontendCustomizations.value[id]
  }

  fun getStatusFilter(): RunDashboardStatusFilter {
    return statusFilter
  }

  override fun updateDashboard(withStructure: Boolean) {
    project.getMessageBus().syncPublisher<ServiceEventListener>(ServiceEventListener.TOPIC).handle(
      ServiceEventListener.ServiceEvent.createResetEvent(RunDashboardServiceViewContributor::class.java))
  }

  /**
   * Returns true if the given run configuration should be shown in the frontend Run Dashboard.
   *
   * Implementation notes:
   * - This logic relies on the frontend DTO model provided by the backend.
   * - In the frontend, a RunConfiguration instance may not carry a real configuration type id.
   *   Matching is therefore performed with lightweight "fake" frontend instances that expose the
   *   real configuration type ids; otherwise the configuration may not be recognized and shown.
   */
  override fun isShowInDashboard(runConfiguration: RunConfiguration): Boolean {
    return frontendDtos.value.firstOrNull {
      it.typeId == runConfiguration.type.id
      && it.name == runConfiguration.name
    } != null
  }

  override fun getTypes(): Set<String> {
    return configurationTypes.value.toSet()
  }

  override fun setTypes(types: Set<String>) {
    LOG.debug("setTypes(${types.size} types) invoked on frontend;")

    configurationTypes.value = types
    // Filter frontend DTOs immediately to instantly remove nodes of just removed types.
    frontendDtos.update { currentDtos ->
      currentDtos.filter { dto -> dto.typeId in types }
    }

    RunDashboardServiceViewContributorHelper.scheduleSetConfigurationTypes(project, types)
    updateDashboard(true)
  }

  override fun getHiddenConfigurations(): Set<RunConfiguration?> {
    LOG.debug("getHiddenConfigurations() invoked on frontend; returning empty set")
    return emptySet()
  }

  override fun hideConfigurations(configurations: MutableCollection<out RunConfiguration?>) {
    LOG.debug("hideConfigurations(${configurations.size}) invoked on frontend; ignored")
  }

  override fun restoreConfigurations(configurations: MutableCollection<out RunConfiguration?>) {
    LOG.debug("restoreConfigurations(${configurations.size}) invoked on frontend; ignored")
  }

  override fun isNewExcluded(typeId: String): Boolean {
    return frontendExcludedConfigurationTypeIds.value.contains(typeId)
  }

  override fun setNewExcluded(typeId: String, newExcluded: Boolean) {
    RunDashboardCoroutineScopeProvider.getInstance(project).cs.launch {
      RunDashboardServiceRpc.getInstance().setNewExcluded(project.projectId(), typeId, newExcluded)
    }
  }

  override fun clearConfigurationStatus(configuration: RunConfiguration) {
    LOG.debug("clearConfigurationStatus(${configuration.name}) invoked on frontend; ignored")
  }

  override fun isOpenRunningConfigInNewTab(): Boolean {
    LOG.debug("isOpenRunningConfigInNewTab() invoked on frontend; returning false")
    return false
  }

  override fun setOpenRunningConfigInNewTab(value: Boolean) {
    LOG.debug("setOpenRunningConfigInNewTab(value=$value) invoked on frontend; ignored")
  }

  override fun getEnableByDefaultTypes(): Set<String?> {
    LOG.debug("getEnableByDefaultTypes() invoked on frontend; returning empty set")
    return emptySet()
  }

  override fun navigateToServiceOnRun(descriptorId: RunContentDescriptorId, focus: Boolean) {
    LOG.debug("navigateToServiceOnRun() invoked on frontend; ignored")
    return
  }

  override fun updateServiceRunContentDescriptor(contentWithNewDescriptor: Content, oldDescriptor: RunContentDescriptor) {
    val descriptor = RunContentManagerImpl.getRunContentDescriptorByContent(contentWithNewDescriptor) ?: return
    val contentId = descriptor.id as? RunContentDescriptorIdImpl ?: return
    val newDtos = ArrayList(frontendDtos.value)
    for ((index, serviceDto) in newDtos.withIndex()) {
      if (serviceDto.contentId == oldDescriptor.id) {
        if (serviceDto is RunDashboardMainServiceDto) {
          val newDto = serviceDto.copy(contentId = contentId)
          newDtos[index] = newDto
        }
        else if (serviceDto is RunDashboardAdditionalServiceDto) {
          val newDto = serviceDto.copy(contentId = contentId)
          newDtos[index] = newDto
        }
        frontendDtos.value = newDtos
        updateDashboard(true)
        RunDashboardServiceViewContributorHelper.scheduleAttachRunContentDescriptorId(project, oldDescriptor.id, contentId)
        break
      }
    }
  }

  /**
   * Binds a run content descriptor to a run configuration service during the execution flow.
   *
   * The binding uses the configurationName and typeId transferred via RPC to locate the corresponding
   * RunConfiguration (RunnerAndConfigurationSettings) service for the currently starting process.
   * This matching is needed only while the execution is being initiated.
   * Once the binding is established, it is stored in the frontend model and propagated back to the backend.
   *
   * As a result, the binding survives subsequent Run Configuration renames, and there is no need to
   * introduce a persistent Run Configuration UUID.
   */
  fun attachServiceRunContentDescriptor(content: Content) {
    val descriptor = RunContentManagerImpl.getRunContentDescriptorByContent(content) ?: return
    val contentId = descriptor.id as? RunContentDescriptorIdImpl ?: return
    val runConfigurationName = descriptor.runConfigurationName ?: return
    val runConfigurationTypeId = descriptor.runConfigurationTypeId ?: return

    val newDtos = ArrayList(frontendDtos.value)
    for ((index, serviceDto) in newDtos.withIndex()) {
      if (serviceDto.name == runConfigurationName && serviceDto.typeId == runConfigurationTypeId && serviceDto is RunDashboardMainServiceDto) {
        if (serviceDto.contentId == contentId) {
          updateDashboard(true)
          return
        }
        else if (serviceDto.contentId == null) {
          val newDto = serviceDto.copy(contentId = contentId)
          newDtos[index] = newDto
        }
        else {
          var lastOfGroup = index
          while (lastOfGroup < newDtos.lastIndex && newDtos[lastOfGroup + 1].uuid == serviceDto.uuid) {
            lastOfGroup = index + 1
          }
          val newDto = serviceDto.toAdditionalServiceDto(contentId)
          newDtos.add(lastOfGroup + 1, newDto)
        }

        frontendDtos.value = newDtos
        updateDashboard(true)
        RunDashboardServiceViewContributorHelper.scheduleAttachRunContentDescriptorId(project, null, contentId)
        break
      }
    }
  }

  fun detachServiceRunContentDescriptor(content: Content) {
    val descriptor = RunContentManagerImpl.getRunContentDescriptorByContent(content) ?: return
    val contentId = descriptor.id ?: return

    val newDtos = ArrayList(frontendDtos.value)
    for ((index, serviceDto) in newDtos.withIndex()) {
      if (serviceDto.contentId == contentId) {
        if (serviceDto is RunDashboardMainServiceDto) {
          val next = if (index < newDtos.lastIndex) {
            val nextCandidate = newDtos[index + 1]
            if (nextCandidate.uuid == serviceDto.uuid) {
              nextCandidate
            }
            else {
              null
            }
          }
          else {
            null
          }
          if (next != null) {
            newDtos.remove(next)
          }
          val newContentId = next?.contentId

          val newDto = serviceDto.copy(contentId = newContentId)

          newDtos[index] = newDto
        }
        else {
          newDtos.remove(serviceDto)
        }
        frontendDtos.value = newDtos
        RunDashboardServiceViewContributorHelper.scheduleDetachRunContentDescriptorId(project, contentId)
        break
      }
    }
  }

  private fun scheduleFetchInitialState(project: Project) {
    val synchronizationScope = RunDashboardCoroutineScopeProvider.getInstance(project).cs.childScope("RunDashboardServiceSynchronizer")
    synchronizationScope.launch {
      subscribeToBackendConfigurationTypesUpdates()
    }
    synchronizationScope.launch {
      subscribeToBackendSettingsUpdates()
    }
    synchronizationScope.launch {
      subscribeToBackendServicesUpdates()
    }
    synchronizationScope.launch {
      subscribeToBackendStatusesUpdates()
    }
    synchronizationScope.launch {
      subscribeToBackendCustomizationsUpdates()
    }
    synchronizationScope.launch {
      subscribeToBackendAvailableConfigurationUpdates()
    }
    synchronizationScope.launch {
      subscribeToBackendExcludedConfigurationUpdates()
    }
    synchronizationScope.launch {
      subscribeToNavigateToServiceEvents()
    }
    synchronizationScope.launch {
      FrontendRunDashboardLuxHolder.getInstance(project).subscribeToRunToolwindowUpdates()
    }
  }

  fun getSettings(): RunDashboardSettingsDto {
    return frontendSettings.value
  }

  companion object {
    private val LOG = Logger.getInstance(FrontendRunDashboardManager::class.java)

    @JvmStatic
    fun getInstance(project: Project): FrontendRunDashboardManager {
      return project.getService(FrontendRunDashboardManager::class.java)
    }
  }
}
