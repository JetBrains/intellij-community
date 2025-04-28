// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.newui

import com.intellij.ide.plugins.PluginEnableDisableAction
import com.intellij.ide.plugins.PluginEnabledState
import com.intellij.ide.plugins.marketplace.IntellijPluginMetadata
import com.intellij.ide.plugins.marketplace.MarketplaceSearchPluginData
import com.intellij.ide.plugins.marketplace.PluginReviewComment
import javax.swing.JComponent
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.extensions.PluginId
import org.jetbrains.annotations.ApiStatus

/*
 * A service that executes operations on plugins, depending on the source of the plugin.
 * If it is local, the operation will be executed locally; otherwise, it will be executed on the server using an RPC call.
 */
@ApiStatus.Internal
class PluginModelFacade(private val pluginModel: MyPluginModel) {
  private val localController = LocalPluginManagerController(pluginModel)

  fun isPluginInstallingOrUpdating(model: PluginUiModel): Boolean {
    return getController(model).isPluginInstallingOrUpdating(model)
  }

  fun getState(model: PluginUiModel): PluginEnabledState {
    return getController(model).getState(model)
  }

  fun enable(model: PluginUiModel) {
    getController(model).enable(model)
  }

  fun disable(model: PluginUiModel) {
    getController(model).disable(model)
  }

  fun installOrUpdatePlugin(component: JComponent, model: PluginUiModel, updateDescriptor: PluginUiModel?, modalityState: ModalityState) {
    getController(model).installOrUpdatePlugin(component, model, updateDescriptor?.getDescriptor(), modalityState)
  }

  fun addUninstalled(model: PluginUiModel) {
    getController(model).addUninstalled(model)
  }

  fun getIcon(model: PluginUiModel, big: Boolean, error: Boolean, disabled: Boolean): javax.swing.Icon {
    return getController(model).getIcon(model, big, error, disabled)
  }

  fun getErrors(model: PluginUiModel): List<com.intellij.openapi.util.text.HtmlChunk> {
    return pluginModel.getErrors(model.getDescriptor())
  }

  fun enableRequiredPlugins(model: PluginUiModel) {
    getController(model).enableRequiredPlugins(model)
  }

  fun isUninstalled(model: PluginUiModel): Boolean {
    return pluginModel.isUninstalled(model.getDescriptor())
  }

  fun isEnabled(model: PluginUiModel): Boolean {
    return getController(model).isEnabled(model)
  }

  fun finishInstall(model: PluginUiModel, installedDescriptor: com.intellij.ide.plugins.IdeaPluginDescriptorImpl?, success: Boolean, showErrors: Boolean, restartRequired: Boolean) {
    pluginModel.finishInstall(model.getDescriptor(), installedDescriptor, success, showErrors, restartRequired)
  }

  fun isPluginRequiredForProject(model: PluginUiModel): Boolean {
    return getController(model).isPluginRequiredForProject(model)
  }

  fun addComponent(component: ListPluginComponent) {
    pluginModel.addComponent(component)
  }

  fun removeComponent(component: ListPluginComponent) {
    pluginModel.removeComponent(component)
  }

  fun hasPluginRequiresUltimateButItsDisabled(models: Collection<PluginUiModel>): Boolean {
    return models.groupBy { it.source }.any { getController(it.key).hasPluginRequiresUltimateButItsDisabled(it.value) }
  }

  fun setEnabledState(models: Collection<PluginUiModel>, action: PluginEnableDisableAction) {
    models.groupBy { it.source }.forEach {  getController(it.key).setEnabledState(it.value, action)}
  }

  fun getDependents(models: Collection<PluginUiModel>): Map<PluginUiModel, List<PluginUiModel>> {
    return models.groupBy { it.source}.map { getController(it.key).getDependents(it.value) }.reduce { acc, map -> acc + map }
  }

  fun isBundledUpdate(model: PluginUiModel?): Boolean {
    if(model == null) return false
    return getController(model).isBundledUpdate(model)
  }

  fun uninstallAndUpdateUi(descriptor: PluginUiModel) {
    return getController(descriptor).uninstallAndUpdateUi(descriptor)
  }

  fun findInstalledPlugin(model: PluginUiModel): PluginUiModel? {
    return getController(model).findInstalledPlugin(model)
  }

  fun findPlugin(model: PluginUiModel): PluginUiModel? {
    return getController(model).findPlugin(model)
  }

  fun getPluginManagerUrl(model: PluginUiModel): String {
    return getController(model).getPluginManagerUrl(model)
  }

  fun isDisabledInDiff(model: PluginUiModel): Boolean {
    return getController(model).isDisabledInDiff(model)
  }

  fun loadPluginDetails(model: PluginUiModel): PluginUiModel? {
    return getController(model).loadPluginDetails(model)
  }

  fun loadAllPluginDetails(existingModel: PluginUiModel, targetModel: PluginUiModel): PluginUiModel? {
    return getController(existingModel).loadAllPluginDetails(existingModel, targetModel)
  }

  fun getLastCompatiblePluginUpdate(model: PluginUiModel): PluginUiModel? {
    return getController(model).getLastCompatiblePluginUpdate(model)
  }

  fun loadPluginMetadata(source: PluginSource, pluginId: String): IntellijPluginMetadata? {
    return getController(source).loadPluginMetadata(pluginId)
  }

  fun loadReviews(existingModel: PluginUiModel): PluginUiModel? {
    return getController(existingModel).fetchReviews(existingModel)
  }

  fun loadDependencyNames(targetModel: PluginUiModel): PluginUiModel? {
    return getController(targetModel.source).fetchDependecyNames(targetModel)
  }

  fun loadPluginReviews(targetModel: PluginUiModel, page: Int): List<PluginReviewComment> {
    return getController(targetModel.source).loadPluginReviews(targetModel, page)
  }

  fun isLoaded(model: PluginUiModel): Boolean {
    return getController(model).isLoaded(model)
  }

  private fun getController(model: PluginUiModel): PluginManagerController {
    return getController(model.source)
  }
  private fun getController(source: PluginSource): PluginManagerController{
    return when (source) {
      PluginSource.LOCAL -> localController
      PluginSource.REMOTE -> TODO()
    }
  }
  //Temporary field that will allow as to change code in small parts
  fun getModel(): MyPluginModel = pluginModel

  companion object {

    @JvmStatic
    fun addProgress(model: PluginUiModel, indicator: com.intellij.openapi.wm.ex.ProgressIndicatorEx) {
      MyPluginModel.addProgress(model.getDescriptor(), indicator)
    }
    @JvmStatic
    fun removeProgress(model: PluginUiModel, indicator: com.intellij.openapi.wm.ex.ProgressIndicatorEx) {
      MyPluginModel.removeProgress(model.getDescriptor(), indicator)
    }
  }
}

