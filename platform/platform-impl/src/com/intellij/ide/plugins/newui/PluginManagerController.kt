// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.newui

import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.ide.plugins.PluginEnableDisableAction
import com.intellij.ide.plugins.PluginEnabledState
import com.intellij.ide.plugins.marketplace.IntellijPluginMetadata
import com.intellij.ide.plugins.marketplace.MarketplaceSearchPluginData
import com.intellij.ide.plugins.marketplace.PluginReviewComment
import com.intellij.openapi.application.ModalityState
import org.jetbrains.annotations.ApiStatus
import javax.swing.Icon
import javax.swing.JComponent

@ApiStatus.Internal
interface PluginManagerController {
  fun isPluginInstallingOrUpdating(model: PluginUiModel): Boolean
  fun getState(model: PluginUiModel): PluginEnabledState
  fun disable(model: PluginUiModel)
  fun installOrUpdatePlugin(component: JComponent, model: PluginUiModel, updateDescriptor: IdeaPluginDescriptor?, modalityState: ModalityState)
  fun addUninstalled(model: PluginUiModel)
  fun enable(model: PluginUiModel)
  fun getIcon(model: PluginUiModel, big: Boolean, error: Boolean, disabled: Boolean): Icon
  fun enableRequiredPlugins(model: PluginUiModel)
  fun isEnabled(model: PluginUiModel): Boolean
  fun isPluginRequiredForProject(model: PluginUiModel): Boolean
  fun hasPluginRequiresUltimateButItsDisabled(models: List<PluginUiModel>): Boolean
  fun setEnabledState(models: List<PluginUiModel>, action: PluginEnableDisableAction)
  fun getDependents(models: List<PluginUiModel>): Map<PluginUiModel, List<PluginUiModel>>
  fun isBundledUpdate(model: PluginUiModel): Boolean
  fun uninstallAndUpdateUi(model: PluginUiModel)
  fun findInstalledPlugin(model: PluginUiModel): PluginUiModel?
  fun getPluginManagerUrl(model: PluginUiModel) : String
  fun isDisabledInDiff(model: PluginUiModel): Boolean
  fun findPlugin(model: PluginUiModel): PluginUiModel?
  fun loadPluginDetails(model: PluginUiModel): PluginUiModel?
  fun loadAllPluginDetails(existingModel: PluginUiModel, targetModel: PluginUiModel): PluginUiModel?
  fun getLastCompatiblePluginUpdate(model: PluginUiModel): PluginUiModel?
  fun loadPluginMetadata(pluginId: String): IntellijPluginMetadata?
  fun fetchReviews(targetModel: PluginUiModel): PluginUiModel?
  fun fetchDependecyNames(targetModel: PluginUiModel): PluginUiModel?
  fun loadPluginReviews(targetModel: PluginUiModel, page: Int): List<PluginReviewComment>
}