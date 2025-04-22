// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.newui

import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.ide.plugins.PluginEnableDisableAction
import com.intellij.ide.plugins.PluginEnabledState
import javax.swing.JComponent
import com.intellij.openapi.application.ModalityState
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

@ApiStatus.Internal
interface PluginManagerController {
  fun isPluginInstallingOrUpdating(model: PluginUiModel): Boolean
  fun getState(model: PluginUiModel): PluginEnabledState
  fun disable(model: PluginUiModel)
  fun installOrUpdatePlugin(component: JComponent, model: PluginUiModel, updateDescriptor: IdeaPluginDescriptor?, modalityState: ModalityState)
  fun addUninstalled(model: PluginUiModel)
  fun enable(model: PluginUiModel)
  fun getIcon(model: PluginUiModel, big: Boolean, error: Boolean, disabled: Boolean): javax.swing.Icon
  fun enableRequiredPlugins(model: PluginUiModel)
  fun isEnabled(model: PluginUiModel): Boolean
  fun isPluginRequiredForProject(model: PluginUiModel): Boolean
  fun hasPluginRequiresUltimateButItsDisabled(models: List<PluginUiModel>): Boolean
  fun setEnabledState(models: List<PluginUiModel>, action: PluginEnableDisableAction)
  fun getDependents(models: List<PluginUiModel>): Map<PluginUiModel, List<PluginUiModel>>
  fun isBundledUpdate(model: PluginUiModel): Boolean
  fun uninstallAndUpdateUi(model: PluginUiModel)
}