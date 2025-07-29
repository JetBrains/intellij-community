// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.newui

import com.intellij.ide.plugins.marketplace.ApplyPluginsStateResult
import com.intellij.ide.plugins.marketplace.CheckErrorsResult
import com.intellij.ide.plugins.marketplace.IdeCompatibleUpdate
import com.intellij.ide.plugins.marketplace.InitSessionResult
import com.intellij.ide.plugins.marketplace.IntellijPluginMetadata
import com.intellij.ide.plugins.marketplace.PluginReviewComment
import com.intellij.ide.plugins.marketplace.PluginSearchResult
import com.intellij.ide.plugins.marketplace.PrepareToUninstallResult
import com.intellij.ide.plugins.marketplace.SetEnabledStateResult
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.registry.Registry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.jetbrains.annotations.ApiStatus
import java.util.UUID
import javax.swing.JComponent

/*
  Executes operations on plugins. Will have several implementations depending on registry option/rem dev mode.
  It serves the same purpose as PluginModelFacade but is stateless.
 */
@Service
@ApiStatus.Internal
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

  fun executeMarketplaceQuery(query: String, count: Int, includeUpgradeToCommercialIde: Boolean): PluginSearchResult {
    return getController().executePluginsSearch(query, count, includeUpgradeToCommercialIde)
  }

  fun getVisiblePlugins(showImplementationDetails: Boolean): List<PluginUiModel> {
    return getController().getVisiblePlugins(showImplementationDetails)
  }

  fun getInstalledPlugins(): List<PluginUiModel> {
    return getController().getInstalledPlugins()
  }

  fun getUpdateModels(): List<PluginUiModel> {
    return getController().getUpdates()
  }

  fun loadPluginDetails(model: PluginUiModel): PluginUiModel? {
    return getController().loadPluginDetails(model)
  }

  fun loadPluginReviews(pluginId: PluginId, page: Int): List<PluginReviewComment>? {
    return getController().loadPluginReviews(pluginId, page)
  }

  fun allowLoadUnloadWithoutRestart(pluginId: PluginId): Boolean {
    return getController().allowLoadUnloadWithoutRestart(pluginId)
  }

  fun resetSession(sessionId: String, removeSession: Boolean, parentComponent: JComponent? = null, callback: (Map<PluginId, Boolean>) -> Unit = {}) {
    service<FrontendRpcCoroutineContext>().coroutineScope.launch(Dispatchers.IO)  {
      callback(getController().resetSession(sessionId, removeSession, parentComponent))
    }
  }

  suspend fun loadErrors(sessionId: String): Map<PluginId, CheckErrorsResult> {
    return getController().loadErrors(sessionId)
  }

  fun getPlugin(pluginId: PluginId): PluginUiModel? {
    return getController().getPlugin(pluginId)
  }

  fun isPluginInstalled(pluginId: PluginId): Boolean {
    return getController().isPluginInstalled(pluginId)
  }

  fun hasPluginsAvailableForEnableDisable(pluginIds: List<PluginId>): Boolean {
    return getController().hasPluginsAvailableForEnableDisable(pluginIds)
  }

  fun setPluginStatus(sessionId: String, pluginIds: List<PluginId>, enable: Boolean) {
    getController().setPluginStatus(sessionId, pluginIds, enable)
  }

  fun applySession(sessionId: String, parent: JComponent? = null, project: Project?): ApplyPluginsStateResult {
    return getController().applySession(sessionId, parent, project)

  }

  @NlsSafe
  fun getApplySessionError(sessionId: String): String? {
    return getController().getApplyError(sessionId)
  }

  suspend fun updatePluginDependencies(sessionId: String): Set<PluginId> {
    return getController().updatePluginDependencies(sessionId)
  }

  suspend fun isModified(sessionId: String): Boolean {
    return getController().isModified(sessionId)
  }

  suspend fun findInstalledPlugins(plugins: Set<PluginId>): Map<PluginId, PluginUiModel> {
    return getController().findInstalledPlugins(plugins)
  }

  fun enablePlugins(sessionId: String, descriptorIds: List<PluginId>, enable: Boolean, project: Project?): SetEnabledStateResult {
    return getController().enablePlugins(sessionId, descriptorIds, enable, project)
  }

  fun prepareToUninstall(pluginsToUninstall: List<PluginId>): PrepareToUninstallResult {
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

  fun enableRequiredPlugins(sessionId: String, pluginId: PluginId): Set<PluginId> {
    return getController().enableRequiredPlugins(sessionId, pluginId)
  }

  fun getCustomRepoPlugins(): List<PluginUiModel> {
    return getController().getCustomRepoPlugins()
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

  fun findPluginNames(pluginIds: List<PluginId>): List<String> {
    return getController().findPluginNames(pluginIds)
  }

  suspend fun findPlugin(pluginId: PluginId): PluginUiModel? {
    return getController().findPlugin(pluginId)
  }

  fun getLastCompatiblePluginUpdateModel(pluginId: PluginId, buildNumber: String? = null, indicator: ProgressIndicator? = null): PluginUiModel? {
    return getController().getLastCompatiblePluginUpdateModel(pluginId, buildNumber, indicator)
  }

  fun getLastCompatiblePluginUpdate(allIds: Set<PluginId>, throwExceptions: Boolean, buildNumber: String? = null): List<IdeCompatibleUpdate> {
    return getController().getLastCompatiblePluginUpdate(allIds, throwExceptions, buildNumber)
  }

  fun loadPluginMetadata(externalPluginId: String): IntellijPluginMetadata? {
    return getController().loadPluginMetadata(externalPluginId)
  }

  fun getAllPluginsTags(): Set<String> {
    return getController().getAllPluginsTags()
  }

  fun getAllVendors(): Set<String> {
    return getController().getAllVendors()
  }

  fun updateDescriptorsForInstalledPlugins() {
    getController().updateDescriptorsForInstalledPlugins()
  }

  fun isNeedUpdate(pluginId: PluginId): Boolean {
    return getController().isNeedUpdate(pluginId)
  }

  fun getPluginInstallationState(pluginId: PluginId): PluginInstallationState {
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