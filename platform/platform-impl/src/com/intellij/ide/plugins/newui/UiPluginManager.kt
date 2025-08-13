// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.newui

import com.intellij.ide.plugins.PluginEnabler
import com.intellij.ide.plugins.marketplace.*
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.IntellijInternalApi
import com.intellij.openapi.util.registry.Registry
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.jetbrains.annotations.ApiStatus
import java.util.*
import javax.swing.JComponent

/*
  Executes operations on plugins. Will have several implementations depending on registry option/rem dev mode.
  It serves the same purpose as PluginModelFacade but is stateless.
 */
@Service
@ApiStatus.Internal
@IntellijInternalApi
class UiPluginManager {
  suspend fun getPlugins(): List<PluginUiModel> {
    return getController().getPlugins()
  }

  fun closeSession(uuid: String) {
    service<FrontendRpcCoroutineContext>().coroutineScope.launch(Dispatchers.IO) {
      getController().closeSession(uuid)
    }
  }

  suspend fun initSession(uuid: UUID): InitSessionResult {
    return getController().initSession(uuid.toString())
  }

  suspend fun executeMarketplaceQuery(query: String, count: Int, includeUpgradeToCommercialIde: Boolean): PluginSearchResult {
    return getController().executePluginsSearch(query, count, includeUpgradeToCommercialIde)
  }

  suspend fun getVisiblePlugins(showImplementationDetails: Boolean): List<PluginUiModel> {
    return getController().getVisiblePlugins(showImplementationDetails)
  }

  suspend fun getInstalledPlugins(): List<PluginUiModel> {
    return getController().getInstalledPlugins()
  }

  suspend fun getUpdateModels(): List<PluginUiModel> {
    return getController().getUpdates()
  }

  suspend fun loadPluginDetails(model: PluginUiModel): PluginUiModel? {
    return getController().loadPluginDetails(model)
  }

  suspend fun loadPluginReviews(pluginId: PluginId, page: Int): List<PluginReviewComment>? {
    return getController().loadPluginReviews(pluginId, page)
  }

  fun resetSession(sessionId: String, removeSession: Boolean, parentComponent: JComponent? = null, callback: (Map<PluginId, Boolean>) -> Unit = {}) {
    service<FrontendRpcCoroutineContext>().coroutineScope.launch(Dispatchers.IO) {
      callback(getController().resetSession(sessionId, removeSession, parentComponent))
    }
  }

  suspend fun loadErrors(sessionId: String): Map<PluginId, CheckErrorsResult> {
    return getController().loadErrors(sessionId)
  }

  suspend fun getPlugin(pluginId: PluginId): PluginUiModel? {
    return getController().getPlugin(pluginId)
  }

  suspend fun isPluginInstalled(pluginId: PluginId): Boolean {
    return getController().isPluginInstalled(pluginId)
  }

  suspend fun getPluginsRequiresUltimateMap(pluginIds: List<PluginId>): Map<PluginId, Boolean> {
    return getController().getPluginsRequiresUltimateMap(pluginIds)
  }

  fun setPluginStatus(sessionId: String, pluginIds: List<PluginId>, enable: Boolean) {
    getController().setPluginStatus(sessionId, pluginIds, enable)
  }

  suspend fun applySession(sessionId: String, parent: JComponent? = null, project: Project?): ApplyPluginsStateResult {
    return getController().applySession(sessionId, parent, project)
  }

  suspend fun updatePluginDependencies(sessionId: String): Set<PluginId> {
    return getController().updatePluginDependencies(sessionId)
  }

  suspend fun isModified(sessionId: String): Boolean {
    return getController().isModified(sessionId)
  }

  suspend fun loadDescriptorById(pluginId: PluginId): PluginUiModel? {
    return getController().loadDescriptorById(pluginId)
  }

  suspend fun findInstalledPlugins(plugins: Set<PluginId>): Map<PluginId, PluginUiModel> {
    return getController().findInstalledPlugins(plugins)
  }

  fun findInstalledPluginsSync(plugins: Set<PluginId>): Map<PluginId, PluginUiModel> {
    return runBlockingMaybeCancellable { findInstalledPlugins(plugins) }
  }

  fun getInstallationStatesSync(): Map<PluginId, PluginInstallationState> {
    return runBlockingMaybeCancellable { getInstallationStates() }
  }

  suspend fun getInstallationStates(): Map<PluginId, PluginInstallationState> {
    return getController().getPluginInstallationStates()
  }

  fun enablePlugins(sessionId: String, descriptorIds: List<PluginId>, enable: Boolean, project: Project?): SetEnabledStateResult {
    return getController().enablePlugins(sessionId, descriptorIds, enable, project)
  }

  suspend fun prepareToUninstall(pluginsToUninstall: List<PluginId>): PrepareToUninstallResult {
    return getController().prepareToUninstall(pluginsToUninstall)
  }

  fun isPluginRequiresUltimateButItIsDisabled(sessionId: String, pluginId: PluginId): Boolean {
    return getController().isPluginRequiresUltimateButItIsDisabled(sessionId, pluginId)
  }

  fun hasPluginRequiresUltimateButItsDisabled(pluginIds: List<PluginId>): Boolean {
    return getController().hasPluginRequiresUltimateButItsDisabled(pluginIds)
  }

  fun filterPluginsRequiringUltimateButItsDisabled(pluginIds: List<PluginId>): List<PluginId> {
    return getController().filterPluginsRequiringUltimateButItsDisabled(pluginIds)
  }

  suspend fun enableRequiredPlugins(sessionId: String, pluginId: PluginId): Set<PluginId> {
    return getController().enableRequiredPlugins(sessionId, pluginId)
  }

  //Not going to block EDT, because no real IO operations are needed
  @Suppress("RAW_RUN_BLOCKING")
  fun getCustomRepoTags(): Set<String> {
    return runBlocking { getController().getCustomRepoTags() }
  }

  suspend fun getCustomRepositoryPluginMap(): Map<String, List<PluginUiModel>> {
    return getController().getCustomRepositoryPluginMap()
  }

  suspend fun isDisabledInDiff(sessionId: String, pluginId: PluginId): Boolean {
    return getController().isDisabledInDiff(sessionId, pluginId)
  }

  suspend fun getErrors(sessionId: String, pluginId: PluginId): CheckErrorsResult {
    return getController().getErrors(sessionId, pluginId)
  }

  fun getErrorsSync(sessionId: String, pluginId: PluginId): CheckErrorsResult {
    return runBlockingMaybeCancellable { getErrors(sessionId, pluginId) }
  }

  fun setEnableStateForDependencies(sessionId: String, descriptorIds: Set<PluginId>, enable: Boolean): SetEnabledStateResult {
    return getController().setEnableStateForDependencies(sessionId, descriptorIds, enable)
  }

  suspend fun findPluginNames(pluginIds: List<PluginId>): List<String> {
    return getController().findPluginNames(pluginIds)
  }

  suspend fun findPlugin(pluginId: PluginId): PluginUiModel? {
    return getController().findPlugin(pluginId)
  }

  suspend fun continueInstallation(sessionId: String, pluginId: PluginId, project: Project, enableRequiredPlugins: Boolean, allowInstallWithoutRestart: Boolean, pluginEnabler: PluginEnabler?, modalityState: ModalityState?, parentComponent: JComponent?): InstallPluginResult {
    return getController().continueInstallation(sessionId, pluginId, project, enableRequiredPlugins, allowInstallWithoutRestart, pluginEnabler, modalityState, parentComponent)
  }

  suspend fun getLastCompatiblePluginUpdateModel(pluginId: PluginId, buildNumber: String? = null, indicator: ProgressIndicator? = null): PluginUiModel? {
    return getController().getLastCompatiblePluginUpdateModel(pluginId, buildNumber, indicator)
  }

  suspend fun getLastCompatiblePluginUpdate(allIds: Set<PluginId>, throwExceptions: Boolean, buildNumber: String? = null): List<IdeCompatibleUpdate> {
    return getController().getLastCompatiblePluginUpdate(allIds, throwExceptions, buildNumber)
  }

  suspend fun loadPluginMetadata(externalPluginId: String): IntellijPluginMetadata? {
    return getController().loadPluginMetadata(externalPluginId)
  }

  fun getAllPluginsTags(): Set<String> {
    return getController().getAllPluginsTags()
  }

  fun getAllVendors(): Set<String> {
    return getController().getAllVendors()
  }

  fun updateDescriptorsForInstalledPlugins() {
    service<FrontendRpcCoroutineContext>().coroutineScope.launch(Dispatchers.IO) {
      getController().updateDescriptorsForInstalledPlugins()
    }
  }

  @RequiresBackgroundThread(generateAssertion = false)
  fun isNeedUpdate(pluginId: PluginId): Boolean {
    return runBlockingMaybeCancellable { getController().isNeedUpdate(pluginId) }
  }

  suspend fun getPluginInstallationState(pluginId: PluginId): PluginInstallationState {
    return getController().getPluginInstallationState(pluginId)
  }

  fun getController(): UiPluginManagerController {
    if (Registry.`is`("reworked.plugin.manager.enabled", false)) {
      return UiPluginManagerController.EP_NAME.extensionList.firstOrNull { it.isEnabled() } ?: DefaultUiPluginManagerController
    }
    return DefaultUiPluginManagerController
  }

  fun subscribeToUpdatesCount(sessionId: String, callback: (Int?) -> Unit): PluginUpdatesService {
    return getController().connectToUpdateServiceWithCounter(sessionId, callback)
  }

  companion object {
    @JvmStatic
    fun getInstance(): UiPluginManager = service()
  }
}


@Service
@ApiStatus.Internal
class FrontendRpcCoroutineContext(val coroutineScope: CoroutineScope)