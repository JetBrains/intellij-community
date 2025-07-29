// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.newui

import com.intellij.ide.plugins.InstallPluginRequest
import com.intellij.ide.plugins.PluginEnabler
import com.intellij.ide.plugins.marketplace.ApplyPluginsStateResult
import com.intellij.ide.plugins.marketplace.CheckErrorsResult
import com.intellij.ide.plugins.marketplace.IdeCompatibleUpdate
import com.intellij.ide.plugins.marketplace.InitSessionResult
import com.intellij.ide.plugins.marketplace.InstallPluginResult
import com.intellij.ide.plugins.marketplace.IntellijPluginMetadata
import com.intellij.ide.plugins.marketplace.PluginReviewComment
import com.intellij.ide.plugins.marketplace.PluginSearchResult
import com.intellij.ide.plugins.marketplace.PrepareToUninstallResult
import com.intellij.ide.plugins.marketplace.SetEnabledStateResult
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus
import javax.swing.JComponent

/*
 A controller that executes operations on plugins. There will be several implementations. It serves the same purpose as PluginModelFacade but is stateless.
 */
@ApiStatus.Internal
interface UiPluginManagerController {
  fun isEnabled(): Boolean
  fun getTarget(): PluginSource
  suspend fun getPlugins(): List<PluginUiModel>
  fun getVisiblePlugins(showImplementationDetails: Boolean): List<PluginUiModel>
  suspend fun initSession(sessionId: String): InitSessionResult
  fun getInstalledPlugins(): List<PluginUiModel>
  fun getUpdates(): List<PluginUiModel>
  fun isPluginDisabled(pluginId: PluginId): Boolean
  fun executePluginsSearch(query: String, count: Int, includeIncompatible: Boolean): PluginSearchResult
  fun loadPluginDetails(model: PluginUiModel): PluginUiModel?
  fun loadPluginReviews(pluginId: PluginId, page: Int): List<PluginReviewComment>?
  fun loadPluginMetadata(externalPluginId: String): IntellijPluginMetadata?
  suspend fun closeSession(sessionId: String)
  fun uninstallDynamicPlugin(parentComponent: JComponent?, sessionId: String, pluginId: PluginId, isUpdate: Boolean): Boolean
  fun deletePluginFiles(pluginId: PluginId)
  fun allowLoadUnloadWithoutRestart(pluginId: PluginId): Boolean
  fun getPlugin(id: PluginId): PluginUiModel?
  fun allowLoadUnloadSynchronously(pluginId: PluginId): Boolean
  fun performUninstall(sessionId: String, pluginId: PluginId): Boolean
  fun performInstallOperation(installPluginRequest: InstallPluginRequest, parentComponent: JComponent?, modalityState: ModalityState?, progressIndicator: ProgressIndicator?, pluginEnabler: PluginEnabler, installCallback: (InstallPluginResult) -> Unit)
  fun applySession(sessionId: String, parent: JComponent? = null, project: Project?): ApplyPluginsStateResult
  suspend fun updatePluginDependencies(sessionId: String): Set<PluginId>
  fun enablePlugins(sessionId: String, descriptorIds: List<PluginId>, enable: Boolean, project: Project?): SetEnabledStateResult
  fun prepareToUninstall(pluginsToUninstall: List<PluginId>): PrepareToUninstallResult
  suspend fun isBundledUpdate(pluginIds: List<PluginId>): Boolean
  fun isPluginRequiresUltimateButItIsDisabled(sessionId: String, pluginId: PluginId): Boolean
  fun hasPluginRequiresUltimateButItsDisabled(pluginIds: List<PluginId>): Boolean
  fun enableRequiredPlugins(sessionId: String, pluginId: PluginId): Set<PluginId>
  fun getCustomRepoPlugins(): List<PluginUiModel>
  suspend fun getCustomRepositoryPluginMap(): Map<String, List<PluginUiModel>>
  suspend fun isDisabledInDiff(sessionId: String, pluginId: PluginId): Boolean
  suspend fun getErrors(sessionId: String, pluginId: PluginId): CheckErrorsResult
  fun isPluginInstalled(pluginId: PluginId): Boolean
  fun hasPluginsAvailableForEnableDisable(pluginIds: List<PluginId>): Boolean
  fun setEnableStateForDependencies(sessionId: String, descriptorIds: Set<PluginId>, enable: Boolean): SetEnabledStateResult
  fun filterPluginsRequiringUltimateButItsDisabled(pluginIds: List<PluginId>): List<PluginId>
  fun findPluginNames(pluginIds: List<PluginId>): List<String>
  suspend fun findPlugin(pluginId: PluginId): PluginUiModel?

  fun getLastCompatiblePluginUpdateModel(pluginId: PluginId, buildNumber: String? = null, indicator: ProgressIndicator? = null): PluginUiModel?
  fun getLastCompatiblePluginUpdate(allIds: Set<PluginId>, throwExceptions: Boolean, buildNumber: String? = null): List<IdeCompatibleUpdate>
  fun updateDescriptorsForInstalledPlugins()
  fun isNeedUpdate(pluginId: PluginId): Boolean
  fun connectToUpdateServiceWithCounter(sessionId: String, callback: (Int?) -> Unit): PluginUpdatesService
  fun getAllPluginsTags(): Set<String>
  fun getAllVendors(): Set<String>
  fun getPluginInstallationState(pluginId: PluginId): PluginInstallationState
  fun getPluginInstallationStates(): Map<PluginId, PluginInstallationState>
  suspend fun checkPluginCanBeDownloaded(pluginUiModel: PluginUiModel, progressIndicator: ProgressIndicator?): Boolean
  fun setPluginStatus(sessionId: String, pluginIds: List<PluginId>, enable: Boolean)
  fun getApplyError(sessionId: String): String?

  suspend fun loadErrors(sessionId: String): Map<PluginId, CheckErrorsResult>
  suspend fun isModified(sessionId: String): Boolean

  suspend fun resetSession(sessionId: String, removeSession: Boolean, parentComponent: JComponent? = null): Map<PluginId, Boolean>
  suspend fun isPluginEnabled(pluginId: PluginId): Boolean
  suspend fun findInstalledPlugins(plugins: Set<PluginId>): Map<PluginId, PluginUiModel>

  companion object {
    val EP_NAME: ExtensionPointName<UiPluginManagerController> = ExtensionPointName<UiPluginManagerController>("com.intellij.uiPluginManagerController")
  }
}