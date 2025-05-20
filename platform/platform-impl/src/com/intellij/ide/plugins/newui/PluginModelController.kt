// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.newui

import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.ide.plugins.PluginEnableDisableAction
import com.intellij.ide.plugins.PluginEnabledState
import com.intellij.ide.plugins.marketplace.IntellijPluginMetadata
import com.intellij.ide.plugins.marketplace.PluginReviewComment
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.util.text.HtmlChunk
import org.jetbrains.annotations.ApiStatus
import javax.swing.Icon
import javax.swing.JComponent

@ApiStatus.Internal
interface PluginModelController {
  fun isPluginInstallingOrUpdating(model: PluginUiModel): Boolean
  fun getState(model: PluginUiModel): PluginEnabledState
  fun disable(models: List<PluginUiModel>)
  fun installOrUpdatePlugin(component: JComponent, model: PluginUiModel, updateDescriptor: PluginUiModel?, modalityState: ModalityState)
  fun addUninstalled(model: PluginUiModel)
  fun enable(models: List<PluginUiModel>)
  fun getIcon(model: PluginUiModel, big: Boolean, error: Boolean, disabled: Boolean): Icon
  fun enableRequiredPlugins(model: PluginUiModel)
  fun isEnabled(model: PluginUiModel): Boolean
  fun isPluginRequiredForProject(model: PluginUiModel): Boolean
  fun hasPluginRequiresUltimateButItsDisabled(models: List<PluginUiModel>): Boolean
  fun hasPluginForEnableDisable(models: List<PluginUiModel>): Boolean
  fun setEnabledState(models: List<PluginUiModel>, action: PluginEnableDisableAction)
  fun uninstallAndUpdateUi(model: PluginUiModel)
  fun findInstalledPlugin(model: PluginUiModel): PluginUiModel?
  fun getPluginManagerUrl(model: PluginUiModel): String
  fun isDisabledInDiff(model: PluginUiModel): Boolean
  fun findPlugin(model: PluginUiModel): PluginUiModel?
  fun loadPluginDetails(model: PluginUiModel): PluginUiModel?
  fun loadAllPluginDetails(existingModel: PluginUiModel, targetModel: PluginUiModel): PluginUiModel?
  fun getLastCompatiblePluginUpdate(model: PluginUiModel): PluginUiModel?
  fun loadPluginMetadata(pluginId: String): IntellijPluginMetadata?
  fun fetchReviews(targetModel: PluginUiModel): PluginUiModel?
  fun fetchDependecyNames(targetModel: PluginUiModel): PluginUiModel?
  fun loadPluginReviews(targetModel: PluginUiModel, page: Int): List<PluginReviewComment>
  fun isLoaded(pluginUiModel: PluginUiModel): Boolean
  fun finishInstall(model: PluginUiModel, installedModel: PluginUiModel?, finishedSuccessfully: Boolean, showErrors: Boolean, restartRequired: Boolean)
  fun getErrors(model: PluginUiModel): List<HtmlChunk>
  fun isUninstalled(model: PluginUiModel): Boolean
}