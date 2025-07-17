// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.newui

import com.intellij.ide.plugins.PluginEnableDisableAction
import com.intellij.ide.plugins.PluginEnabledState
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.util.IntellijInternalApi
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.openapi.wm.ex.ProgressIndicatorEx
import org.jetbrains.annotations.ApiStatus
import javax.swing.JComponent

@ApiStatus.Internal
@IntellijInternalApi
open class PluginModelFacade(private val pluginModel: MyPluginModel) {

  fun isPluginInstallingOrUpdating(model: PluginUiModel): Boolean {
    return MyPluginModel.isInstallingOrUpdate(model.pluginId)
  }

  fun closeSession() {
    UiPluginManager.getInstance().closeSession(getModel().sessionId)
  }

  open fun getState(model: PluginUiModel): PluginEnabledState {
    return pluginModel.getState(model.pluginId)
  }

  fun enable(model: PluginUiModel) {
    pluginModel.enable(listOf(model.getDescriptor()))
  }

  fun enable(models: Collection<PluginUiModel>) {
    pluginModel.enable(models.map { it.getDescriptor() })
  }

  fun disable(model: PluginUiModel) {
    pluginModel.disable(listOf(model.getDescriptor()))
  }

  fun disable(models: Collection<PluginUiModel>) {
    pluginModel.disable(models.map { it.getDescriptor() })
  }

  @JvmOverloads
  fun installOrUpdatePlugin(component: JComponent?, model: PluginUiModel, updateDescriptor: PluginUiModel?, modalityState: ModalityState, controller: UiPluginManagerController = UiPluginManager.getInstance().getController(), callback: (Boolean) -> Unit = {}) {
    pluginModel.installOrUpdatePlugin(component, model, updateDescriptor, modalityState, controller, callback)
  }

  fun addUninstalled(pluginId: PluginId) {
    pluginModel.addUninstalled(pluginId)
  }

  fun getIcon(model: PluginUiModel, big: Boolean, error: Boolean, disabled: Boolean): javax.swing.Icon {
    return pluginModel.getIcon(model.getDescriptor(), big, error, disabled)
  }

  fun getErrors(model: PluginUiModel): List<HtmlChunk> {
    return pluginModel.getErrors(model.getDescriptor())
  }

  fun enableRequiredPlugins(model: PluginUiModel) {
    pluginModel.enableRequiredPlugins(model.getDescriptor())
  }

  fun isUninstalled(pluginId: PluginId): Boolean {
    return pluginModel.isUninstalled(pluginId)
  }

  fun isEnabled(model: PluginUiModel): Boolean {
    return pluginModel.isEnabled(model.getDescriptor())
  }

  fun finishInstall(model: PluginUiModel, installedModel: PluginUiModel?, success: Boolean, showErrors: Boolean, restartRequired: Boolean, errors: Map<PluginId, List<HtmlChunk>>) {
    pluginModel.finishInstall(model, installedModel, errors, success, showErrors, restartRequired)
  }

  fun isPluginRequiredForProject(model: PluginUiModel): Boolean {
    return pluginModel.isRequiredPluginForProject(model.pluginId)
  }

  fun addComponent(component: ListPluginComponent) {
    pluginModel.addComponent(component)
  }

  fun removeComponent(component: ListPluginComponent) {
    pluginModel.removeComponent(component)
  }

  fun setEnabledState(models: Collection<PluginUiModel>, action: PluginEnableDisableAction) {
    pluginModel.setEnabledStateAsync(models.map { it.getDescriptor() }, action)
  }

  @JvmOverloads
  fun uninstallAndUpdateUi(descriptor: PluginUiModel, controller: UiPluginManagerController = UiPluginManager.getInstance().getController()) {
    pluginModel.uninstallAndUpdateUi(descriptor, controller)
  }

  suspend fun isDisabledInDiff(model: PluginUiModel): Boolean {
    return UiPluginManager.getInstance().isDisabledInDiff(pluginModel.sessionId.toString(), model.pluginId)
  }

  fun isLoaded(model: PluginUiModel): Boolean {
    return pluginModel.isLoaded(model.pluginId)
  }

  fun getModel(): MyPluginModel = pluginModel

  companion object {
    @JvmStatic
    fun addProgress(model: PluginUiModel, indicator: ProgressIndicatorEx) {
      MyPluginModel.addProgress(model.getDescriptor(), indicator)
    }

    @JvmStatic
    fun removeProgress(model: PluginUiModel, indicator: ProgressIndicatorEx) {
      MyPluginModel.removeProgress(model.getDescriptor(), indicator)
    }
  }
}