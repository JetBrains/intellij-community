// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.newui

import com.intellij.ide.plugins.InstallPluginRequest
import com.intellij.ide.plugins.PluginEnabler
import com.intellij.ide.plugins.marketplace.*
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.FUSEventSource
import com.intellij.openapi.util.IntellijInternalApi
import org.jetbrains.annotations.ApiStatus
import javax.swing.JComponent

/*
 A controller that executes operations on plugins. There will be several implementations. It serves the same purpose as PluginModelFacade but is stateless.
 */
@ApiStatus.Internal
@IntellijInternalApi
interface UiPluginManagerController {
  fun isEnabled(): Boolean
  fun getTarget(): PluginSource
  suspend fun getPlugins(): List<PluginUiModel>
  suspend fun getVisiblePlugins(showImplementationDetails: Boolean): List<PluginUiModel>
  suspend fun initSession(sessionId: String): InitSessionResult
  suspend fun getInstalledPlugins(): List<PluginUiModel>
  suspend fun getUpdates(): List<PluginUiModel>
  suspend fun executePluginsSearch(query: String, count: Int, includeIncompatible: Boolean): PluginSearchResult
  suspend fun loadPluginDetails(model: PluginUiModel): PluginUiModel?
  suspend fun loadPluginReviews(pluginId: PluginId, page: Int): List<PluginReviewComment>?
  suspend fun loadPluginMetadata(externalPluginId: String): IntellijPluginMetadata?
  suspend fun closeSession(sessionId: String)
  suspend fun getPlugin(id: PluginId): PluginUiModel?
  suspend fun performUninstall(sessionId: String, pluginId: PluginId): Boolean
  suspend fun installOrUpdatePlugin(sessionId: String, project: Project, parentComponent: JComponent?, descriptor: PluginUiModel, updateDescriptor: PluginUiModel?, installSource: FUSEventSource?, modalityState: ModalityState?, pluginEnabler: PluginEnabler?): InstallPluginResult
  suspend fun continueInstallation(sessionId: String, pluginId: PluginId, project: Project, enableRequiredPlugins: Boolean, allowInstallWithoutRestart: Boolean, pluginEnabler: PluginEnabler?, modalityState: ModalityState?, parentComponent: JComponent?): InstallPluginResult
  suspend fun applySession(sessionId: String, parent: JComponent? = null, project: Project?): ApplyPluginsStateResult
  suspend fun updatePluginDependencies(sessionId: String): Set<PluginId>
  suspend fun prepareToUninstall(pluginsToUninstall: List<PluginId>): PrepareToUninstallResult
  suspend fun isBundledUpdate(pluginIds: List<PluginId>): Boolean
  suspend fun enableRequiredPlugins(sessionId: String, pluginId: PluginId): Set<PluginId>
  suspend fun getCustomRepositoryPluginMap(): Map<String, List<PluginUiModel>>
  suspend fun isDisabledInDiff(sessionId: String, pluginId: PluginId): Boolean
  suspend fun getErrors(sessionId: String, pluginId: PluginId): CheckErrorsResult
  suspend fun isPluginInstalled(pluginId: PluginId): Boolean
  suspend fun findPluginNames(pluginIds: List<PluginId>): List<String>
  suspend fun findPlugin(pluginId: PluginId): PluginUiModel?

  suspend fun getLastCompatiblePluginUpdateModel(pluginId: PluginId, buildNumber: String? = null, indicator: ProgressIndicator? = null): PluginUiModel?
  suspend fun getLastCompatiblePluginUpdate(allIds: Set<PluginId>, throwExceptions: Boolean, buildNumber: String? = null): List<IdeCompatibleUpdate>
  suspend fun updateDescriptorsForInstalledPlugins()
  suspend fun isNeedUpdate(pluginId: PluginId): Boolean
  suspend fun getPluginInstallationState(pluginId: PluginId): PluginInstallationState
  suspend fun getPluginInstallationStates(): Map<PluginId, PluginInstallationState>
  suspend fun checkPluginCanBeDownloaded(pluginUiModel: PluginUiModel, progressIndicator: ProgressIndicator?): Boolean
  suspend fun getCustomRepoTags(): Set<String>

  fun enablePlugins(sessionId: String, descriptorIds: List<PluginId>, enable: Boolean, project: Project?): SetEnabledStateResult
  fun setEnableStateForDependencies(sessionId: String, descriptorIds: Set<PluginId>, enable: Boolean): SetEnabledStateResult
  fun setPluginStatus(sessionId: String, pluginIds: List<PluginId>, enable: Boolean)
  fun isPluginRequiresUltimateButItIsDisabled(sessionId: String, pluginId: PluginId): Boolean
  fun hasPluginRequiresUltimateButItsDisabled(pluginIds: List<PluginId>): Boolean
  fun filterPluginsRequiringUltimateButItsDisabled(pluginIds: List<PluginId>): List<PluginId>
  fun getAllPluginsTags(): Set<String>
  fun getAllVendors(): Set<String>
  fun connectToUpdateServiceWithCounter(sessionId: String, callback: (Int?) -> Unit): PluginUpdatesService

  suspend fun loadErrors(sessionId: String): Map<PluginId, CheckErrorsResult>
  suspend fun isModified(sessionId: String): Boolean

  suspend fun resetSession(sessionId: String, removeSession: Boolean, parentComponent: JComponent? = null): Map<PluginId, Boolean>
  suspend fun isPluginEnabled(pluginId: PluginId): Boolean
  suspend fun findInstalledPlugins(plugins: Set<PluginId>): Map<PluginId, PluginUiModel>
  suspend fun loadDescriptorById(pluginId: PluginId): PluginUiModel?
  suspend fun getPluginsRequiresUltimateMap(pluginIds: List<PluginId>): Map<PluginId, Boolean>

  companion object {
    val EP_NAME: ExtensionPointName<UiPluginManagerController> = ExtensionPointName<UiPluginManagerController>("com.intellij.uiPluginManagerController")
  }
}