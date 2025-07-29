// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.newui

import com.intellij.ide.IdeBundle
import com.intellij.ide.plugins.*
import com.intellij.ide.plugins.DynamicPlugins.allowLoadUnloadWithoutRestart
import com.intellij.ide.plugins.PluginManagerCore.buildPluginIdMap
import com.intellij.ide.plugins.PluginManagerCore.getLoadingError
import com.intellij.ide.plugins.PluginManagerCore.getPluginSet
import com.intellij.ide.plugins.PluginManagerCore.isCompatible
import com.intellij.ide.plugins.PluginManagerCore.isDisabled
import com.intellij.ide.plugins.PluginManagerCore.isIncompatible
import com.intellij.ide.plugins.PluginManagerCore.isUpdatedBundledPlugin
import com.intellij.ide.plugins.PluginManagerCore.looksLikePlatformPluginAlias
import com.intellij.ide.plugins.PluginUtils.toPluginDescriptors
import com.intellij.ide.plugins.api.PluginDto
import com.intellij.ide.plugins.marketplace.*
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.application.ex.ApplicationInfoEx
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.updateSettings.impl.PluginDownloader
import com.intellij.openapi.updateSettings.impl.UpdateChecker
import com.intellij.openapi.util.BuildNumber
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresReadLockAbsence
import fleet.util.associateNotNull
import fleet.util.associateWithNotNull
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import java.io.File
import java.io.IOException
import java.nio.file.FileVisitResult
import java.util.*
import javax.swing.JComponent

@ApiStatus.Internal
object DefaultUiPluginManagerController : UiPluginManagerController {
  private val LOG = Logger.getInstance(DefaultUiPluginManagerController::class.java)

  override fun isEnabled(): Boolean = true

  override fun getTarget(): PluginSource = PluginSource.LOCAL

  override suspend fun getPlugins(): List<PluginUiModel> {
    return PluginManagerCore.plugins.map { PluginUiModelAdapter(it).withSource() }
  }

  override suspend fun initSession(sessionId: String): InitSessionResult {
    return initSessionSync(sessionId)
  }


  fun initSessionSync(sessionId: String): InitSessionResult {
    val session = createSession(sessionId)
    val applicationInfo = ApplicationInfo.getInstance()
    val visiblePlugins = mutableListOf<PluginUiModel>()
    for (plugin in getInstalledAndPendingPlugins()) {
      val pluginId: PluginId = plugin.pluginId
      if (applicationInfo.isEssentialPlugin(pluginId)) {
        session.pluginStates[pluginId] = PluginEnabledState.ENABLED
      }
      else {
        val state = if (isDisabled(pluginId)) PluginEnabledState.DISABLED else if (plugin.isEnabled()) PluginEnabledState.ENABLED else null
        session.pluginStates[pluginId] = state
        visiblePlugins.add(PluginUiModelAdapter(plugin))
      }
    }
    return InitSessionResult(visiblePlugins, session.pluginStates.mapValues { it.value?.isEnabled })
  }

  override fun getVisiblePlugins(showImplementationDetails: Boolean): List<PluginUiModel> {
    return PluginManager.getVisiblePlugins(showImplementationDetails).map { PluginUiModelAdapter(it) }.toList().withSource()
  }

  override fun getInstalledPlugins(): List<PluginUiModel> {
    return InstalledPluginsState.getInstance().installedPlugins.map { PluginUiModelAdapter(it) }.withSource()
  }

  override fun getUpdates(): List<PluginUiModel> {
    return PluginUpdatesService.getUpdates()?.map { PluginUiModelAdapter(it) }?.withSource() ?: emptyList()
  }

  override fun getPlugin(id: PluginId): PluginUiModel? {
    return PluginManagerCore.getPlugin(id)?.let { PluginUiModelAdapter(it) }?.withSource()
  }

  override suspend fun findPlugin(pluginId: PluginId): PluginUiModel? {
    return buildPluginIdMap()[pluginId]?.let { PluginUiModelAdapter(it) }?.withSource()
  }

  override fun isPluginDisabled(pluginId: PluginId): Boolean {
    return PluginManagerCore.isDisabled(pluginId)
  }

  override fun isPluginInstalled(pluginId: PluginId): Boolean {
    return PluginManagerCore.isPluginInstalled(pluginId)
  }

  override fun isNeedUpdate(pluginId: PluginId): Boolean {
    val descriptor = PluginManagerCore.getPlugin(pluginId) ?: return false
    return PluginUpdatesService.isNeedUpdate(descriptor)
  }

  override suspend fun isBundledUpdate(pluginIds: List<PluginId>): Boolean {
    val pluginIdMap = buildPluginIdMap()
    return pluginIds.map { pluginIdMap[it] }.all { isBundledUpdate(it) }
  }

  override fun getCustomRepoPlugins(): List<PluginUiModel> {
    return CustomPluginRepositoryService.getInstance().getCustomRepositoryPlugins().toList().withSource()
  }

  override suspend fun getCustomRepositoryPluginMap(): Map<String, List<PluginUiModel>> {
    return CustomPluginRepositoryService.getInstance().getCustomRepositoryPluginMap()
  }

  override fun findPluginNames(pluginIds: List<PluginId>): List<String> {
    val requests = MarketplaceRequests.getInstance()
    return pluginIds.map {
      PluginManagerCore.findPlugin(it)?.name ?: requests.getLastCompatiblePluginUpdate(it)?.name ?: it.idString
    }
  }

  fun createSession(sessionId: String): PluginManagerSession {
    return PluginManagerSessionService.getInstance().createSession(sessionId)
  }

  override suspend fun closeSession(sessionId: String) {
    PluginManagerSessionService.getInstance().removeSession(sessionId)
  }

  override suspend fun isModified(sessionId: String): Boolean {
    val session = findSession(sessionId) ?: return false
    return session.dynamicPluginsToInstall.isNotEmpty() ||
           session.dynamicPluginsToUninstall.isNotEmpty() ||
           session.pluginsToRemoveOnCancel.isNotEmpty() ||
           session.statesDiff.isNotEmpty()
  }

  override fun applySession(sessionId: String, parent: JComponent?, project: Project?): ApplyPluginsStateResult {
    val session = findSession(sessionId) ?: return ApplyPluginsStateResult()
    var needRestart = session.needRestart
    if (ApplicationManager.getApplication().isExitInProgress) {
      needRestart = true
    }
    val pluginIdMap = buildPluginIdMap()
    val contentModuleIdMap = getPluginSet().buildContentModuleIdMap()
    val pluginsToEnable = updatePluginDependencies(session, pluginIdMap, contentModuleIdMap)
    val errorMessage = getApplyError(session, pluginIdMap)
    if (errorMessage != null) {
      return ApplyPluginsStateResult(error = errorMessage)
    }

    val pluginEnabler = PluginEnabler.getInstance()
    val uninstallsRequiringRestart: MutableSet<PluginId> = mutableSetOf()
    for (pluginDescriptor in session.dynamicPluginsToUninstall) {
      session.statesDiff.remove(pluginDescriptor)
      val pluginId: PluginId = pluginDescriptor.getPluginId()

      if (!needRestart) {
        needRestart = !uninstallDynamicPlugin(parent, sessionId, pluginDescriptor.getPluginId(), false)
      }

      if (needRestart) {
        uninstallsRequiringRestart.add(pluginId)
        try {
          PluginInstaller.uninstallAfterRestart(pluginDescriptor)
        }
        catch (e: IOException) {
          LOG.error(e)
        }
      }
      else {
        session.pluginStates.remove(pluginId)
      }
    }

    for (pendingPluginInstall in session.dynamicPluginsToInstall.values) {
      val pluginId: PluginId = pendingPluginInstall.pluginDescriptor.getPluginId()
      if (!needRestart && !uninstallsRequiringRestart.contains(pluginId)) {
        InstalledPluginsState.getInstance().trackPluginInstallation {
          needRestart = !PluginInstaller.installAndLoadDynamicPlugin(pendingPluginInstall.file, parent, pendingPluginInstall.pluginDescriptor)
        }
      }

      if (needRestart) {
        try {
          PluginInstaller.installAfterRestartAndKeepIfNecessary(pendingPluginInstall.pluginDescriptor, pendingPluginInstall.file, null)
        }
        catch (e: IOException) {
          LOG.error(e)
        }
      }
    }

    if (needRestart) {
      InstalledPluginsState.getInstance().isRestartRequired = true
    }

    session.dynamicPluginsToInstall.clear()
    session.pluginsToRemoveOnCancel.clear()

    needRestart = needRestart or !applyEnableDisablePlugins(session, pluginEnabler, parent, project)
    session.dynamicPluginsToUninstall.clear()
    session.statesDiff.clear()

    if (needRestart) {
      InstalledPluginsState.getInstance().isRestartRequired = true
    }

    session.isUiDisposedWithApply = true
    return ApplyPluginsStateResult(pluginsToEnable, needRestart)
  }

  override suspend fun resetSession(sessionId: String, removeSession: Boolean, parentComponent: JComponent?): Map<PluginId, Boolean> {
    val changedStates = mutableMapOf<PluginId, Boolean>()
    val sessionManager = PluginManagerSessionService.getInstance()
    val session = findSession(sessionId) ?: return changedStates

    session.statesDiff.forEach {
      session.pluginStates[it.key.pluginId] = it.value.second
      changedStates[it.key.pluginId] = it.value.second.isEnabled
    }
    session.statesDiff.clear();

    session.pluginsToRemoveOnCancel.forEach {
      PluginInstaller.uninstallDynamicPlugin(parentComponent, it, false)
    }
    session.pluginsToRemoveOnCancel.clear()
    if (removeSession) {
      sessionManager.removeSession(sessionId)
    }
    return changedStates
  }

  override suspend fun isPluginEnabled(pluginId: PluginId): Boolean {
    return !PluginManagerCore.isDisabled(pluginId)
  }

  override suspend fun findInstalledPlugins(plugins: Set<PluginId>): Map<PluginId, PluginUiModel> {
    return plugins.mapNotNull { getPlugin(it) }.associateBy { it.pluginId }
  }

  override fun connectToUpdateServiceWithCounter(sessionId: String, callback: (Int?) -> Unit): PluginUpdatesService {
    val session = PluginManagerSessionService.getInstance().getSession(sessionId)
    val service = PluginUpdatesService.connectWithCounter(callback)
    service.setFilter { session?.isPluginEnabled(it.pluginId) ?: true }
    session?.updateService = service
    return service
  }

  override fun getAllPluginsTags(): Set<String> {
    return MarketplaceRequests.getInstance().marketplaceTagsSupplier.get()
  }

  override fun getAllVendors(): Set<String> {
    return MarketplaceRequests.getInstance().marketplaceVendorsSupplier.get()
  }

  override fun getPluginInstallationState(pluginId: PluginId): PluginInstallationState {
    val pluginsState = InstalledPluginsState.getInstance()
    val plugin = PluginManagerCore.getPlugin(pluginId)
    val isDeleted = (plugin as? IdeaPluginDescriptorImpl)?.isDeleted ?: false
    val status = when {
      pluginsState.wasInstalledWithoutRestart(pluginId) -> PluginStatus.INSTALLED_WITHOUT_RESTART
      pluginsState.wasUninstalledWithoutRestart(pluginId) || isDeleted -> PluginStatus.UNINSTALLED_WITHOUT_RESTART
      pluginsState.wasInstalled(pluginId) -> PluginStatus.INSTALLED_AND_REQUIRED_RESTART
      pluginsState.wasUpdatedWithRestart(pluginId) -> PluginStatus.UPDATED_WITH_RESTART
      pluginsState.wasUpdated(pluginId) -> PluginStatus.UPDATED
      else -> null
    }
    return PluginInstallationState(plugin != null, status)
  }

  override fun getPluginInstallationStates(): Map<PluginId, PluginInstallationState> {
    return getAllInstalledPlugins().associateWith { pluginId ->
      return@associateWith getPluginInstallationState(pluginId)
    }
  }

  override fun performInstallOperation(
    request: InstallPluginRequest,
    parentComponent: JComponent?,
    modalityState: ModalityState?,
    progressIndicator: ProgressIndicator?,
    pluginEnabler: PluginEnabler,
    installCallback: (InstallPluginResult) -> Unit,
  ) {
    val session = findSession(request.sessionId) ?: return
    val result = InstallPluginResult()
    val operation = PluginInstallOperation(request.pluginsToInstall, getCustomRepoPlugins(), progressIndicator
                                                                                             ?: BgProgressIndicator(), pluginEnabler)
    operation.setAllowInstallWithoutRestart(request.allowInstallWithoutRestart)
    var cancel = false
    var success = true
    var showErrors = true
    var restartRequired = true
    val pluginsToInstallSynchronously: MutableList<PendingDynamicPluginInstall> = mutableListOf()
    try {
      operation.run()
      for (install in operation.pendingDynamicPluginInstalls) {
        if (DynamicPlugins.allowLoadUnloadSynchronously(install.pluginDescriptor)) {
          pluginsToInstallSynchronously.add(install)
          session.pluginsToRemoveOnCancel.add(install.pluginDescriptor)
        }
        else {
          session.dynamicPluginsToInstall.put(install.pluginDescriptor.getPluginId(), install)
        }
      }

      success = operation.isSuccess
      showErrors = !operation.isShownErrors
      restartRequired = operation.isRestartRequired
    }
    catch (e: ProcessCanceledException) {
      cancel = true
    }
    catch (e: Throwable) {
      LOG.error(e)
      success = false
    }

    result.success = success
    result.cancel = cancel
    result.showErrors = showErrors
    result.restartRequired = restartRequired
    session.needRestart = session.needRestart || restartRequired

    ApplicationManager.getApplication().invokeLater(Runnable {
      installDynamicPluginsSynchronously(request, pluginsToInstallSynchronously, session, parentComponent, result, installCallback)
    }, modalityState ?: ModalityState.defaultModalityState())
  }

  override fun updateDescriptorsForInstalledPlugins() {
    UpdateChecker.updateDescriptorsForInstalledPlugins(InstalledPluginsState.getInstance())
  }

  override fun performUninstall(sessionId: String, pluginId: PluginId): Boolean {
    val uninstalledPlugin = uninstallPlugin(pluginId)
    val session = findSession(sessionId) ?: return false

    session.uninstalledPlugins.add(pluginId)
    if (uninstalledPlugin != null) {
      session.dynamicPluginsToUninstall.add(uninstalledPlugin)
    }
    session.needRestart = session.needRestart || uninstalledPlugin == null
    return uninstalledPlugin == null
  }

  override fun uninstallDynamicPlugin(parentComponent: JComponent?, sessionId: String, pluginId: PluginId, isUpdate: Boolean): Boolean {
    val session = findSession(sessionId) ?: return true
    val descriptorImpl = PluginManagerCore.findPlugin(pluginId) ?: return false
    val uninstalledWithoutRestart = PluginInstaller.uninstallDynamicPlugin(parentComponent, descriptorImpl, isUpdate)
    session.needRestart = session.needRestart || !uninstalledWithoutRestart
    return uninstalledWithoutRestart
  }

  override fun deletePluginFiles(pluginId: PluginId) {
    val descriptorImpl = PluginManagerCore.findPlugin(pluginId) ?: return
    try {
      FileUtil.delete(descriptorImpl.pluginPath)
    }
    catch (e: IOException) {
      LOG.debug(e)
    }
  }

  override suspend fun checkPluginCanBeDownloaded(pluginUiModel: PluginUiModel, progressIndicator: ProgressIndicator?): Boolean {
    return PluginDownloader.createDownloader(pluginUiModel.getDescriptor(), pluginUiModel.repositoryName, null).checkPluginCanBeDownloaded(null)
  }

  override suspend fun loadErrors(sessionId: String): Map<PluginId, CheckErrorsResult> {
    val session = findSession(sessionId) ?: return emptyMap()
    return getPlugins().map { it.pluginId }.associateWith { getErrors(session, it) }
  }

  override fun prepareToUninstall(pluginsToUninstall: List<PluginId>): PrepareToUninstallResult {
    val applicationInfo = ApplicationInfoEx.getInstanceEx()
    val idMap = buildPluginIdMap()
    val contentModuleIdMap = getPluginSet().buildContentModuleIdMap()
    val dependentsMap = pluginsToUninstall.associateWith { getDependents(it, applicationInfo, idMap, contentModuleIdMap).map { it.name } }
    val bundledPlugins = pluginsToUninstall
      .mapNotNull { idMap[it] }
      .filter { isBundledUpdate(it) }
      .map { it.pluginId }
    return PrepareToUninstallResult(dependentsMap, bundledPlugins)
  }

  override fun setPluginStatus(sessionId: String, pluginIds: List<PluginId>, enable: Boolean) {
    val session = findSession(sessionId) ?: return
    setEnabledState(session, pluginIds, enable)
  }

  override fun enablePlugins(
    sessionId: String,
    descriptorIds: List<PluginId>,
    enable: Boolean,
    project: Project?,
  ): SetEnabledStateResult {
    val session = PluginManagerSessionService.getInstance().getSession(sessionId) ?: return SetEnabledStateResult()
    val tempEnabled: MutableMap<PluginId, PluginEnabledState?> = session.pluginStates.toMutableMap()
    val descriptors = descriptorIds.toPluginDescriptors()

    val action = if (enable) PluginEnableDisableAction.ENABLE_GLOBALLY else PluginEnableDisableAction.DISABLE_GLOBALLY
    setNewEnabled(descriptors, tempEnabled, action)

    val pluginIdMap: Map<PluginId, IdeaPluginDescriptorImpl> = buildPluginIdMap()
    val contentModuleIdMap = getPluginSet().buildContentModuleIdMap()
    val descriptorsToUpdate = if (action.isEnable) {
      getDependenciesToEnable(descriptors, tempEnabled, pluginIdMap, contentModuleIdMap)
    }
    else getDependentsToDisable(descriptorIds, tempEnabled, pluginIdMap, contentModuleIdMap)

    val pluginNamesToUpdate = descriptorsToUpdate
      .filter { !InstalledPluginsTableModel.isHiddenImplementationDetail(it) }
      .map { it.getName() }
      .toSet()

    val allDescriptorsToUpdate = descriptors + descriptorsToUpdate
    if (InstalledPluginsTableModel.HIDE_IMPLEMENTATION_DETAILS && pluginNamesToUpdate.isNotEmpty()) {
      return SetEnabledStateResult(pluginNamesToUpdate, allDescriptorsToUpdate.map { it.pluginId }.toSet())
    }
    else {
      return enableDependencies(session, allDescriptorsToUpdate, action, pluginIdMap, contentModuleIdMap)
    }
  }

  override fun setEnableStateForDependencies(
    sessionId: String,
    descriptorIds: Set<PluginId>,
    enable: Boolean,
  ): SetEnabledStateResult {
    val session = findSession(sessionId) ?: return SetEnabledStateResult()
    val descriptors = descriptorIds.toPluginDescriptors()
    val action = if (enable) PluginEnableDisableAction.ENABLE_GLOBALLY else PluginEnableDisableAction.DISABLE_GLOBALLY
    return enableDependencies(session, descriptors, action, buildPluginIdMap(), getPluginSet().buildContentModuleIdMap())
  }

  override fun enableRequiredPlugins(sessionId: String, pluginId: PluginId): Set<PluginId> {
    val session = PluginManagerSessionService.getInstance().getSession(sessionId) ?: return emptySet()
    val requiredPluginIds = getRequiredPluginIds(session, pluginId)
    if (requiredPluginIds.isEmpty()) {
      return emptySet()
    }

    val requiredPlugins: MutableSet<IdeaPluginDescriptor> = mutableSetOf()
    val plugins = getInstalledAndPendingPlugins()
    for (pluginId in requiredPluginIds) {
      var result: IdeaPluginDescriptor? = plugins.find { pluginId == it.pluginId }
      if (result == null && looksLikePlatformPluginAlias(pluginId)) {
        result = plugins.find { it is IdeaPluginDescriptorImpl && it.pluginAliases.contains(pluginId) }
        if (result != null) {
          requiredPlugins.add(result)
        }
      }
      if (result != null) {
        requiredPlugins.add(result)
      }
    }
    return enableDependencies(session, requiredPlugins.toList(), PluginEnableDisableAction.ENABLE_GLOBALLY,
                              buildPluginIdMap(), getPluginSet().buildContentModuleIdMap()).pluginsIdsToSwitch
  }

  override suspend fun isDisabledInDiff(sessionId: String, pluginId: PluginId): Boolean {
    val session = findSession(sessionId) ?: return false
    val descriptor = buildPluginIdMap()[pluginId] ?: return false
    val diffStatePair: Pair<PluginEnableDisableAction, PluginEnabledState>? = session.statesDiff[descriptor]
    return diffStatePair?.second?.isEnabled ?: false
  }

  override fun hasPluginsAvailableForEnableDisable(pluginIds: List<PluginId>): Boolean {
    val idMap = buildPluginIdMap()
    val contentModuleIdMap = getPluginSet().buildContentModuleIdMap()
    return pluginIds.any { !pluginRequiresUltimatePluginButItsDisabled(it, idMap, contentModuleIdMap) }
  }

  override fun isPluginRequiresUltimateButItIsDisabled(sessionId: String, pluginId: PluginId): Boolean {
    val idMap = buildPluginIdMap()
    val contentModuleIdMap = getPluginSet().buildContentModuleIdMap()
    val rootDescriptor = idMap[pluginId] ?: findSession(sessionId)?.dynamicPluginsToInstall[pluginId]?.pluginDescriptor ?: return false
    return pluginRequiresUltimatePluginButItsDisabled(rootDescriptor, idMap, contentModuleIdMap)
  }

  override fun hasPluginRequiresUltimateButItsDisabled(pluginIds: List<PluginId>): Boolean {
    val idMap = buildPluginIdMap()
    val contentModuleIdMap = getPluginSet().buildContentModuleIdMap()
    return pluginIds.any { pluginRequiresUltimatePluginButItsDisabled(it, idMap, contentModuleIdMap) }
  }

  override fun filterPluginsRequiringUltimateButItsDisabled(pluginIds: List<PluginId>): List<PluginId> {
    val idMap = buildPluginIdMap()
    val contentModuleIdMap = getPluginSet().buildContentModuleIdMap()
    return pluginIds.filter { pluginRequiresUltimatePluginButItsDisabled(it, idMap, contentModuleIdMap) }
  }

  override fun allowLoadUnloadWithoutRestart(pluginId: PluginId): Boolean {
    val descriptorImpl = PluginManagerCore.findPlugin(pluginId) ?: return false
    return allowLoadUnloadWithoutRestart(descriptorImpl)
  }

  override fun allowLoadUnloadSynchronously(pluginId: PluginId): Boolean {
    val descriptorImpl = PluginManagerCore.findPlugin(pluginId) ?: return false
    return DynamicPlugins.allowLoadUnloadSynchronously(descriptorImpl)
  }

  override suspend fun updatePluginDependencies(sessionId: String): Set<PluginId> {
    val session = findSession(sessionId) ?: return emptySet()
    return updatePluginDependencies(session, null, null)
  }

  fun updatePluginDependenciesSync(sessionId: String): Set<PluginId> {
    val session = findSession(sessionId) ?: return emptySet()
    return updatePluginDependencies(session, null, null)
  }

  override fun executePluginsSearch(query: String, count: Int, includeIncompatible: Boolean): PluginSearchResult {
    try {
      val plugins = MarketplaceRequests.getInstance().executePluginSearch(query, count, includeIncompatible)
      return PluginSearchResult(plugins)
    }
    catch (e: IOException) {
      LOG.warn(e)
      return PluginSearchResult(emptyList(), e.message)
    }
  }

  override fun loadPluginDetails(
    model: PluginUiModel,
  ): PluginUiModel? {
    return MarketplaceRequests.getInstance().loadPluginDetails(model)
  }

  @RequiresBackgroundThread
  @RequiresReadLockAbsence
  @Throws(IOException::class)
  override fun loadPluginReviews(pluginId: PluginId, page: Int): List<PluginReviewComment>? {
    return MarketplaceRequests.getInstance().loadPluginReviews(pluginId, page)
  }

  @RequiresBackgroundThread
  @RequiresReadLockAbsence
  override fun loadPluginMetadata(externalPluginId: String): IntellijPluginMetadata? {
    return MarketplaceRequests.getInstance().loadPluginMetadata(externalPluginId)
  }

  override fun getLastCompatiblePluginUpdateModel(pluginId: PluginId, buildNumber: String?, indicator: ProgressIndicator?): PluginUiModel? {
    return MarketplaceRequests.getInstance().getLastCompatiblePluginUpdateModel(pluginId, BuildNumber.fromString(buildNumber), indicator)
  }

  override fun getLastCompatiblePluginUpdate(
    allIds: Set<PluginId>,
    throwExceptions: Boolean,
    buildNumber: String?,
  ): List<IdeCompatibleUpdate> {
    return MarketplaceRequests.getLastCompatiblePluginUpdate(allIds, BuildNumber.fromString(buildNumber), throwExceptions)
  }

  override suspend fun getErrors(sessionId: String, pluginId: PluginId): CheckErrorsResult {
    val session = findSession(sessionId) ?: return CheckErrorsResult()
    return getErrors(session, pluginId)
  }

  private fun getErrors(session: PluginManagerSession, pluginId: PluginId): CheckErrorsResult {
    if (InstalledPluginsState.getInstance().wasUninstalledWithoutRestart(pluginId) ||
        InstalledPluginsState.getInstance().wasInstalledWithoutRestart(pluginId)) {
      // we'll actually install the plugin when the configurable is closed; at this time we don't know if there's any loadingError
      return CheckErrorsResult()
    }

    val loadingError = getLoadingError(pluginId)
    val disabledDependency = if (loadingError is PluginDependencyIsDisabled) loadingError.dependencyId else null
    if (disabledDependency == null) {
      return CheckErrorsResult(loadingError = loadingError?.shortMessage, isDisabledDependencyError = true)
    }

    val requiredPlugins = mutableMapOf<PluginId, IdeaPluginDescriptor?>()
    getRequiredPluginsById(session, pluginId).filter {
      val requiredDescriptor = it.getSecond()
      requiredDescriptor == null || !requiredDescriptor.isEnabled()
    }.forEach { requiredPlugins.put(it.getFirst(), it.getSecond()) }
    if (requiredPlugins.isEmpty()) {
      return CheckErrorsResult()
    }

    if (requiredPlugins.entries.none { it.value == null || isIncompatible(it.value!!) }) {
      val pluginNames = requiredPlugins.map { InstalledPluginsTableModel.getPluginNameOrId(it.key, it.value) }
      return CheckErrorsResult(suggestToEnableRequiredPlugins = true,
                               requiredPluginNames = pluginNames.toSet())
    }
    return CheckErrorsResult()
  }

  private fun findSession(sessionId: String): PluginManagerSession? {
    return PluginManagerSessionService.getInstance().getSession(UUID.fromString(sessionId))
  }

  private fun setEnabledState(session: PluginManagerSession, pluginIds: List<PluginId>, enable: Boolean) {
    val state = if (enable) PluginEnabledState.ENABLED else PluginEnabledState.DISABLED
    pluginIds.forEach { session.pluginStates[it] = state }
  }

  private fun applyEnableDisablePlugins(
    session: PluginManagerSession,
    pluginEnabler: PluginEnabler,
    parentComponent: JComponent?,
    project: Project?,
  ): Boolean {
    val descriptorsByAction = EnumMap<PluginEnableDisableAction, MutableList<IdeaPluginDescriptor>>(PluginEnableDisableAction::class.java)

    for (entry in session.statesDiff.entries) {
      val descriptor: IdeaPluginDescriptor = entry.key
      val pluginId = descriptor.getPluginId()

      val pair: Pair<PluginEnableDisableAction, PluginEnabledState>? = entry.value
      val oldState = pair?.getSecond()
      val newState: PluginEnabledState = session.pluginStates[pluginId] ?: PluginEnabledState.ENABLED

      if (session.uninstalledPlugins.contains(descriptor.pluginId) || (InstalledPluginsTableModel.isHiddenImplementationDetail(descriptor) && newState.isDisabled) || session.pluginStates[pluginId] == null /* if enableMap contains null for id => enable/disable checkbox don't touch */) {
        continue
      }

      if (oldState != newState || newState.isDisabled && session.errorPluginsToDisable.contains(pluginId)) {
        descriptorsByAction.computeIfAbsent(pair?.getFirst()) { mutableListOf<IdeaPluginDescriptor>() }.add(descriptor)
      }
    }

    var appliedWithoutRestart = true
    for (entry in descriptorsByAction.entries) {
      val enable = entry.key.isEnable
      val descriptors = entry.value

      val applied: Boolean
      if (pluginEnabler is DynamicPluginEnabler) {
        applied = if (enable) pluginEnabler.enable(descriptors, project) else pluginEnabler.disable(descriptors, project, parentComponent)
      }
      else {
        applied = if (enable) pluginEnabler.enable(descriptors) else pluginEnabler.disable(descriptors)
      }

      appliedWithoutRestart = appliedWithoutRestart and applied
    }
    return appliedWithoutRestart
  }

  private fun enableDependencies(
    session: PluginManagerSession,
    allDescriptorsToUpdate: List<IdeaPluginDescriptor>,
    action: PluginEnableDisableAction,
    pluginIdMap: Map<PluginId, IdeaPluginDescriptorImpl>,
    contentModuleIdMap: Map<String, ContentModuleDescriptor>,
  ): SetEnabledStateResult {
    val changedStates = setNewEnabled(allDescriptorsToUpdate, session.pluginStates, action,
                                      { descriptor, pair -> handleBeforeChangeEnableState(session, descriptor, pair) })
    val pluginsToEnable = updatePluginDependencies(session, pluginIdMap, contentModuleIdMap)
    pluginsToEnable.forEach { changedStates[it] = true }
    return SetEnabledStateResult(changedStates = changedStates)
  }

  private fun handleBeforeChangeEnableState(
    session: PluginManagerSession,
    descriptor: IdeaPluginDescriptor,
    pair: Pair<PluginEnableDisableAction, PluginEnabledState>,
  ) {
    val pluginId = descriptor.getPluginId()
    val oldPair: Pair<PluginEnableDisableAction, PluginEnabledState>? = session.statesDiff[descriptor]

    val oldState = oldPair?.getSecond()
    val newState = pair.getSecond()
    if (oldState != newState) {
      val state = oldState ?: session.pluginStates[pluginId] ?: PluginEnabledState.ENABLED
      session.statesDiff[descriptor] = Pair.create<PluginEnableDisableAction, PluginEnabledState>(pair.getFirst(), state)
    }
    else {
      session.statesDiff.remove(descriptor)
    }

    session.errorPluginsToDisable.remove(pluginId)

    if (newState.isEnabled || descriptor.isEnabled()) {
      return
    }

    if (isIncompatible(descriptor) ||
        isBrokenPlugin(descriptor) ||
        hasProblematicDependencies(session, pluginId)) {
      session.errorPluginsToDisable.add(pluginId)
    }
  }

  private fun setNewEnabled(
    descriptors: List<IdeaPluginDescriptor>,
    enabledMap: MutableMap<PluginId, PluginEnabledState?>,
    action: PluginEnableDisableAction,
    beforeHandler: (IdeaPluginDescriptor, Pair<PluginEnableDisableAction, PluginEnabledState>) -> Unit = { _, _ -> },
  ): MutableMap<PluginId, Boolean> {
    val changedStates = mutableMapOf<PluginId, Boolean>()
    for (descriptor in descriptors) {
      val pluginId = descriptor.pluginId
      val oldState = enabledMap[pluginId]

      val newState = if (oldState == null) PluginEnabledState.DISABLED else action.apply(oldState)
      if (newState != null) {
        beforeHandler(descriptor, Pair.create<PluginEnableDisableAction, PluginEnabledState>(action, newState))
        enabledMap[pluginId] = newState
        changedStates[pluginId] = newState.isEnabled
      }
    }
    return changedStates
  }

  private fun hasProblematicDependencies(session: PluginManagerSession, pluginId: PluginId): Boolean {
    return getRequiredPluginsById(session, pluginId).any {
      val descriptor: IdeaPluginDescriptor? = it.getSecond()
      descriptor != null && !session.isPluginEnabled(descriptor.pluginId)
    }
  }

  private fun getRequiredPluginsById(session: PluginManagerSession, pluginId: PluginId): List<Pair<PluginId, IdeaPluginDescriptorImpl>> {
    val pluginIds: MutableSet<PluginId> = getRequiredPluginIds(session, pluginId)
    if (pluginIds.isEmpty()) {
      return emptyList()
    }

    val pluginIdMap = buildPluginIdMap()
    return pluginIds
      .map {
        val requiredDescriptor = pluginIdMap.get(it)
        val resolvedDescriptor = if (requiredDescriptor == null && looksLikePlatformPluginAlias(it)) PluginManagerCore.findPluginByPlatformAlias(it) else requiredDescriptor
        Pair.create(it, resolvedDescriptor)
      }
  }

  private fun getRequiredPluginIds(session: PluginManagerSession, pluginId: PluginId): MutableSet<PluginId> {
    return session.dependentToRequiredListMap.getOrDefault(pluginId, mutableSetOf())
  }

  private fun installDynamicPluginsSynchronously(
    request: InstallPluginRequest,
    pluginsToInstallSynchronously: List<PendingDynamicPluginInstall>,
    session: PluginManagerSession,
    component: JComponent?,
    result: InstallPluginResult,
    installCallback: (InstallPluginResult) -> Unit,
  ) {
    var dynamicRestartRequired = false
    for (install in pluginsToInstallSynchronously) {
      val installedWithoutRestart = PluginInstaller.installAndLoadDynamicPlugin(install.file, component, install.pluginDescriptor)
      if (installedWithoutRestart) {
        val installedDescriptor = PluginManagerCore.getPlugin(request.pluginId)
        if (installedDescriptor != null) {
          result.installedDescriptor = PluginDto.fromModel(PluginUiModelAdapter(installedDescriptor))
        }
      }
      else {
        dynamicRestartRequired = true
      }
    }
    if (request.finishDynamicInstallationWithoutUi && session.isUiDisposedWithApply) {
      if (session.dynamicPluginsToInstall.size == 1 && session.dynamicPluginsToUninstall.isEmpty() && !request.needRestart) {
        for (pendingPluginInstall in session.dynamicPluginsToInstall.values) {
          InstalledPluginsState.getInstance().trackPluginInstallation(Runnable {
            val requiresRestart = !PluginInstaller.installAndLoadDynamicPlugin(pendingPluginInstall.file, null, pendingPluginInstall.pluginDescriptor)
            result.restartRequired = requiresRestart
            result.dynamicRestartRequired = dynamicRestartRequired or requiresRestart
          })
        }
        session.dynamicPluginsToInstall.clear()
        LOG.info("installed final dynamic plugin")
      }
      if (!session.dynamicPluginsToInstall.isEmpty() || !session.dynamicPluginsToUninstall.isEmpty()) {
        LOG.warn("pending dynamic plugins probably won't finish their installation: " + session.dynamicPluginsToInstall + " " + session.dynamicPluginsToUninstall)
      }
    }
    result.errors = PluginManagerCore.plugins.map { it.pluginId }.associateWith { getErrors(session, it) }
    installCallback(result)
  }

  override fun getApplyError(sessionId: String): String? {
    val session = findSession(sessionId) ?: return null
    return getApplyError(session, buildPluginIdMap())
  }

  @Throws(ConfigurationException::class)
  private fun getApplyError(
    session: PluginManagerSession,
    pluginIdMap: Map<PluginId, IdeaPluginDescriptorImpl>,
  ): @Nls String? {
    val descriptors = mutableListOf<IdeaPluginDescriptorImpl>()
    for (entry in session.dependentToRequiredListMap.entries) {
      val pluginId: PluginId = entry.key

      if (!session.pluginStates.contains(pluginId)) {
        continue
      }

      for (dependencyPluginId in entry.value) {
        if (looksLikePlatformPluginAlias(dependencyPluginId)) {
          continue
        }
        if (session.isPluginDisabled(dependencyPluginId)) {
          // FIXME IJPL-196672 I don't understand why this code exists and what it is supposed to do
          //  (even if it is intended for something, it does not work properly)
          continue
        }

        val descriptor = pluginIdMap[dependencyPluginId]
        if (descriptor != null && !InstalledPluginsTableModel.isHidden(descriptor)) {
          descriptors.add(descriptor)
          LOG.warn("For ${entry.key} dependent plugin: ${descriptor.pluginId} not found")
        }
      }
    }

    if (!descriptors.isEmpty()) {
      val pluginNames = MyPluginModel.getPluginNames(descriptors)
      return IdeBundle.message("dialog.message.unable.to.apply.changes", pluginNames.size, MyPluginModel.joinPluginNamesOrIds(pluginNames))
    }
    return null
  }

  private fun getInstalledAndPendingPlugins(): List<IdeaPluginDescriptor> {
    return InstalledPluginsState.getInstance().installedPlugins + PluginManagerCore.plugins
  }

  private fun getAllInstalledPlugins(): Set<PluginId> {
    val installedPluginsState = InstalledPluginsState.getInstance()
    val ids = installedPluginsState.installedPlugins.map { it.pluginId } + installedPluginsState.installedWithoutRestartPlugins + PluginManagerCore.plugins.map { it.pluginId }
    return ids.toSet()
  }

  fun isBundledUpdate(descriptor: IdeaPluginDescriptor?): Boolean {
    if (descriptor == null || descriptor.isBundled) {
      return false
    }
    if (isUpdatedBundledPlugin(descriptor)) {
      return true
    }
    if (PluginEnabler.HEADLESS.isDisabled(descriptor.getPluginId())) {
      val path = descriptor.pluginPath
      if (path == null) {
        return false
      }
      val name = path.fileName
      if (name == null) {
        return false
      }
      return File(PathManager.getPreInstalledPluginsPath(), name.toString()).exists()
    }
    return false
  }

  private fun getDependenciesToEnable(
    descriptors: List<IdeaPluginDescriptorImpl>,
    enabledMap: Map<PluginId, PluginEnabledState?>,
    pluginIdMap: Map<PluginId, IdeaPluginDescriptorImpl>,
    contentModuleIdMap: Map<String, ContentModuleDescriptor>,
  ): List<IdeaPluginDescriptor> {
    val result = mutableListOf<IdeaPluginDescriptor>()
    for (descriptor in descriptors) {
      PluginManagerCore.processAllNonOptionalDependencies(descriptor, pluginIdMap, contentModuleIdMap) { dependency ->
        val dependencyId = dependency.pluginId
        val state = enabledMap[dependencyId]

        if (dependencyId != descriptor.pluginId && !(state != null && state.isEnabled)) {
          result.add(dependency)
        }
        FileVisitResult.CONTINUE
      }
    }

    return result
  }

  private fun updatePluginDependencies(
    session: PluginManagerSession,
    pluginIdMap: Map<PluginId, IdeaPluginDescriptorImpl>?,
    contentModuleIdMap: Map<String, ContentModuleDescriptor>?,
  ): Set<PluginId> {
    val pluginsToEnable = mutableSetOf<PluginId>()
    var pluginIdMap = pluginIdMap
    var contentModuleIdMap = contentModuleIdMap
    session.dependentToRequiredListMap.clear()

    val pluginsState = InstalledPluginsState.getInstance()
    for (rootDescriptor in getInstalledAndPendingPlugins()) {
      val pluginId: PluginId = rootDescriptor.getPluginId()
      session.dependentToRequiredListMap.remove(pluginId)
      if (session.uninstalledPlugins.contains(rootDescriptor.pluginId) || session.isPluginDisabled(pluginId)) {
        continue
      }

      if (pluginIdMap == null) {
        pluginIdMap = buildPluginIdMap()
      }
      if (contentModuleIdMap == null) {
        contentModuleIdMap = getPluginSet().buildContentModuleIdMap()
      }

      val loaded: Boolean = session.pluginStates.contains(pluginId)
      if (rootDescriptor is IdeaPluginDescriptorImpl) {
        PluginManagerCore.processAllNonOptionalDependencyIds(rootDescriptor, pluginIdMap, contentModuleIdMap) { depId: PluginId ->
          if (depId == pluginId) {
            return@processAllNonOptionalDependencyIds FileVisitResult.CONTINUE
          }
          if ((!session.pluginStates.contains(depId) && !pluginsState.wasInstalled(depId) && !pluginsState.wasUpdated(depId) && !pluginsState.wasInstalledWithoutRestart(depId)) || session.isPluginDisabled(depId)) {
            session.dependentToRequiredListMap.putIfAbsent(pluginId, mutableSetOf())
            session.dependentToRequiredListMap[pluginId]!!.add(depId)
          }
          FileVisitResult.CONTINUE
        }
      }

      if (!loaded && !session.dependentToRequiredListMap.containsKey(pluginId) && isCompatible(rootDescriptor)) {
        setEnabledState(session, listOf(pluginId), true)
        pluginsToEnable.add(pluginId)
      }
    }
    return pluginsToEnable
  }

  private fun uninstallPlugin(pluginId: PluginId): IdeaPluginDescriptor? {
    val descriptorImpl = PluginManagerCore.findPlugin(pluginId) ?: return null
    descriptorImpl.isDeleted = true
    try {
      if (!isBundledUpdate(descriptorImpl)) {
        val enabler: PluginEnabler = PluginEnabler.HEADLESS
        if (enabler.isDisabled(descriptorImpl.getPluginId())) {
          enabler.enable(mutableListOf(descriptorImpl))
        }
      }
      val needRestartForUninstall = PluginInstaller.prepareToUninstall(descriptorImpl)
      InstalledPluginsState.getInstance().onPluginUninstall(descriptorImpl, !needRestartForUninstall)
      if (!needRestartForUninstall) {
        return descriptorImpl
      }
    }
    catch (e: IOException) {
      LOG.error(e)
    }
    return null
  }

  private fun getDependentsToDisable(
    pluginIds: List<PluginId>,
    enabledMap: MutableMap<PluginId, PluginEnabledState?>,
    pluginIdMap: Map<PluginId, IdeaPluginDescriptorImpl>,
    contentModuleIdMap: Map<String, ContentModuleDescriptor>,
  ): List<IdeaPluginDescriptor> {
    val result = mutableListOf<IdeaPluginDescriptor>()

    for (descriptor in getPluginSet().allPlugins) {
      val pluginId = descriptor.getPluginId()
      if (pluginIds.contains(pluginId) ||
          InstalledPluginsTableModel.isDisabled(pluginId, enabledMap)) {
        continue
      }

      PluginManagerCore.processAllNonOptionalDependencies(descriptor, pluginIdMap, contentModuleIdMap) { dependency: IdeaPluginDescriptorImpl? ->
        val dependencyId = dependency!!.getPluginId()
        if (!enabledMap.contains(dependencyId)) {
          return@processAllNonOptionalDependencies FileVisitResult.TERMINATE
        }

        if (dependencyId != pluginId && pluginIds.contains(dependencyId)) {
          result.add(descriptor)
        }
        FileVisitResult.CONTINUE
      }
    }

    return result
  }


  fun getDependents(
    rootId: PluginId,
    applicationInfo: ApplicationInfoEx,
    pluginIdMap: Map<PluginId, IdeaPluginDescriptorImpl>,
    contentModuleIdMap: Map<String, ContentModuleDescriptor>,
  ): List<IdeaPluginDescriptorImpl> {
    val result = mutableListOf<IdeaPluginDescriptorImpl>()
    for (entry in pluginIdMap.entries) {
      val pluginId = entry.key
      val descriptor = entry.value

      if (pluginId == rootId ||
          applicationInfo.isEssentialPlugin(pluginId) || !descriptor.isEnabled() ||
          InstalledPluginsTableModel.isHidden(descriptor)) {
        continue
      }

      PluginManagerCore.processAllNonOptionalDependencies(descriptor, pluginIdMap, contentModuleIdMap) {
        if (it.pluginId == rootId) {
          result.add(descriptor)
          return@processAllNonOptionalDependencies FileVisitResult.TERMINATE
        }
        FileVisitResult.CONTINUE
      }
    }

    return result
  }

  private fun List<PluginUiModel>.withSource(): List<PluginUiModel> {
    forEach { it.source = PluginSource.LOCAL }
    return this
  }

  private fun PluginUiModel.withSource(): PluginUiModel {
    source = PluginSource.LOCAL
    return this
  }
}