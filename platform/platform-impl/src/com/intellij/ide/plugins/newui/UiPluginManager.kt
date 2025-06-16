// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.newui

import com.intellij.ide.plugins.InstallPluginRequest
import com.intellij.ide.plugins.PluginEnabler
import com.intellij.ide.plugins.marketplace.ApplyPluginsStateResult
import com.intellij.ide.plugins.marketplace.CheckErrorsResult
import com.intellij.ide.plugins.marketplace.IdeCompatibleUpdate
import com.intellij.ide.plugins.marketplace.InstallPluginResult
import com.intellij.ide.plugins.marketplace.IntellijPluginMetadata
import com.intellij.ide.plugins.marketplace.PluginReviewComment
import com.intellij.ide.plugins.marketplace.PluginSearchResult
import com.intellij.ide.plugins.marketplace.PrepareToUninstallResult
import com.intellij.ide.plugins.marketplace.SetEnabledStateResult
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
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
  fun getPlugins(): List<PluginUiModel> {
    return getController().getPlugins()
  }

  fun createSession(uuid: UUID) {
    getController().createSession(uuid.toString())
  }

  fun closeSession(uuid: UUID) {
    getController().closeSession(uuid.toString())
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

  fun isPluginDisabled(pluginId: PluginId): Boolean {
    return getController().isPluginDisabled(pluginId)
  }

  fun loadPluginDetails(model: PluginUiModel): PluginUiModel? {
    return getController().loadPluginDetails(model)
  }

  fun loadPluginReviews(pluginId: PluginId, page: Int): List<PluginReviewComment>? {
    return getController().loadPluginReviews(pluginId, page)
  }

  fun tryUnloadPluginIfAllowed(parentComponent: JComponent?, pluginId: PluginId, isUpdate: Boolean): Boolean {
    return getController().tryUnloadPluginIfAllowed(parentComponent, pluginId, isUpdate)
  }

  fun allowLoadUnloadWithoutRestart(pluginId: PluginId): Boolean {
    return getController().allowLoadUnloadWithoutRestart(pluginId)
  }

  fun unloadDynamicPlugin(parentComponent: JComponent?, pluginId: PluginId, isUpdate: Boolean): Boolean {
    return getController().unloadDynamicPlugin(parentComponent, pluginId, isUpdate)
  }

  fun resetSession(sessionId: String, removeSession: Boolean, parentComponent: JComponent? = null, callback: (Map<PluginId, Boolean>) -> Unit = {}) {
    service<FrontendRpcCoroutineContext>().coroutineScope.launch {
      callback(getController().resetSession(sessionId, removeSession, parentComponent))
    }
  }

  fun uninstallDynamicPlugin(parentComponent: JComponent?, pluginId: PluginId, isUpdate: Boolean): Boolean {
    return getController().uninstallDynamicPlugin(parentComponent, pluginId, isUpdate)
  }

  /*
   * Perform the uninstallation and return whether the process requires a restart.
   */
  fun performUninstall(sessionId: String, id: PluginId): Boolean {
    return getController().performUninstall(sessionId, id)
  }

  fun deletePluginFiles(pluginId: PluginId) {
    return getController().deletePluginFiles(pluginId)
  }

  fun allowLoadUnloadSynchronously(pluginId: PluginId): Boolean {
    return getController().allowLoadUnloadSynchronously(pluginId)
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

  fun performInstallOperation(
    request: InstallPluginRequest,
    parentComponent: JComponent? = null,
    modalityState: ModalityState? = null,
    progressIndicator: ProgressIndicator? = null,
    pluginEnabler: PluginEnabler,
    installCallback: (InstallPluginResult) -> Unit,
  ) {
    getController().performInstallOperation(
      request,
      parentComponent,
      modalityState,
      progressIndicator,
      pluginEnabler,
      installCallback
    )
  }

  fun setPluginStatus(sessionId: String, pluginIds: List<PluginId>, enable: Boolean) {
    getController().setPluginStatus(sessionId, pluginIds, enable)
  }

  fun applySession(sessionId: String, parent: JComponent? = null, project: Project?): ApplyPluginsStateResult {
    return getController().applySession(sessionId, parent, project)
  }

  fun updatePluginDependencies(sessionId: String): Set<PluginId> {
    return getController().updatePluginDependencies(sessionId)
  }

  fun isModified(sessionId: String): Boolean {
    return getController().isModified(sessionId)
  }

  fun enablePlugins(sessionId: String, descriptorIds: List<PluginId>, enable: Boolean, project: Project?): SetEnabledStateResult {
    return getController().enablePlugins(sessionId, descriptorIds, enable, project)
  }

  fun prepareToUninstall(pluginsToUninstall: List<PluginId>): PrepareToUninstallResult {
    return getController().prepareToUninstall(pluginsToUninstall)
  }

  fun isBundledUpdate(pluginIds: List<PluginId>): Boolean {
    return getController().isBundledUpdate(pluginIds)
  }

  fun isPluginRequiresUltimateButItIsDisabled(pluginId: PluginId): Boolean {
    return getController().isPluginRequiresUltimateButItIsDisabled(pluginId)
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

  fun isDisabledInDiff(sessionId: String, pluginId: PluginId): Boolean {
    return getController().isDisabledInDiff(sessionId, pluginId)
  }

  fun getErrors(sessionId: String, pluginId: PluginId): CheckErrorsResult {
    return getController().getErrors(sessionId, pluginId)
  }

  fun setEnableStateForDependencies(sessionId: String, descriptorIds: Set<PluginId>, enable: Boolean): SetEnabledStateResult {
    return getController().setEnableStateForDependencies(sessionId, descriptorIds, enable)
  }

  fun findPluginNames(pluginIds: List<PluginId>): List<String> {
    return getController().findPluginNames(pluginIds)
  }

  fun findPlugin(pluginId: PluginId): PluginUiModel? {
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

  fun getController(): UiPluginManagerController {
    if (Registry.`is`("reworked.plugin.manager.enabled")) {
      return UiPluginManagerController.EP_NAME.extensionList.firstOrNull() ?: DefaultUiPluginManagerController
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