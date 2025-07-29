// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.newui

import com.intellij.ide.plugins.marketplace.CheckErrorsResult
import com.intellij.ide.plugins.marketplace.SetEnabledStateResult
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.application.ex.ApplicationInfoEx
import com.intellij.openapi.components.service
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.HtmlChunk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import javax.swing.JComponent

@ApiStatus.Internal
object PluginModelAsyncOperationsExecutor {
  fun performUninstall(
    cs: CoroutineScope,
    descriptor: PluginUiModel,
    sessionId: String,
    controller: UiPluginManagerController,
    callback: (Boolean, Map<PluginId, CheckErrorsResult>) -> Unit,
  ) {
    cs.launch {
      val needRestart = controller.performUninstall(sessionId, descriptor.pluginId)
      descriptor.isDeleted = true
      PluginManagerCustomizer.getInstance()?.onPluginDeleted(descriptor, controller.getTarget())
      val errors = UiPluginManager.getInstance().loadErrors(sessionId)
      withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
        callback(needRestart, errors)
      }
    }
  }

  @JvmOverloads
  @ApiStatus.Internal
  fun updateErrors(cs: CoroutineScope = service<FrontendRpcCoroutineContext>().coroutineScope, sessionId: String, pluginId: PluginId, callback: (List<HtmlChunk>) -> Unit) {
    cs.launch(Dispatchers.IO) {
      val errors = UiPluginManager.getInstance().getErrors(sessionId, pluginId)
      val htmlChunks = MyPluginModel.getErrors(errors)
      withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
        callback(htmlChunks)
      }
    }
  }

  fun enablePlugins(
    cs: CoroutineScope,
    sessionId: String,
    descriptorIds: List<PluginId>,
    enable: Boolean,
    project: Project?,
    callback: (SetEnabledStateResult) -> Unit,
  ) {
    cs.launch(Dispatchers.IO) {
      val result = UiPluginManager.getInstance().enablePlugins(sessionId, descriptorIds, enable, project)
      withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
        callback(result)
      }
    }
  }

  fun updatePlugin(
    cs: CoroutineScope,
    modelFacade: PluginModelFacade,
    plugin: PluginUiModel,
    updateDescriptor: PluginUiModel?,
    pluginManagerCustomizer: PluginManagerCustomizer?,
    modalityState: ModalityState,
    component: JComponent?,
  ) {
    cs.launch(Dispatchers.IO) {
      val model = pluginManagerCustomizer?.getUpdateButtonCustomizationModel(modelFacade, plugin, updateDescriptor, modalityState)
      withContext(Dispatchers.EDT + modalityState.asContextElement()) {
        if (model != null) {
          model.action()
        }
        else {
          modelFacade.installOrUpdatePlugin(component, plugin, updateDescriptor, modalityState)
        }
      }
    }
  }

  fun updateButtons(
    cs: CoroutineScope,
    installedPluginComponents: List<ListPluginComponent>,
    pluginComponentsMap: Map<PluginId, List<ListPluginComponent>>,
    detailPanels: List<PluginDetailsPageComponent>,
  ) {
    cs.launch(Dispatchers.EDT + ModalityState.any().asContextElement()) {
      for (component in installedPluginComponents) {
        component.updateButtons()
      }
      for (plugins in pluginComponentsMap.values) {
        for (plugin in plugins) {
          if (plugin.myInstalledDescriptorForMarketplace != null) {
            plugin.updateButtons()
          }
        }
      }
      for (detailPanel in detailPanels) {
        detailPanel.updateAll()
      }
    }
  }

  fun createButtons(
    cs: CoroutineScope,
    component: JComponent,
    pluginId: PluginId,
    isMarketplace: Boolean,
    callback: (PluginInstallationState, PluginUiModel?) -> Unit,
  ) {
    cs.launch(Dispatchers.IO) {
      val installationState = UiPluginManager.getInstance().getPluginInstallationState(pluginId)
      val installedDescriptor = if (isMarketplace && installationState.status != PluginStatus.INSTALLED_AND_REQUIRED_RESTART) {
        UiPluginManager.getInstance().getPlugin(pluginId)
      }
      else null
      println("scheduling swithc to edt for ${pluginId.idString}")
      withContext(Dispatchers.EDT + ModalityState.stateForComponent(component).asContextElement()) {
        println("switched to edt for ${pluginId.idString}")
        callback(installationState, installedDescriptor)
      }
    }
  }

  fun switchPlugins(coroutineScope: CoroutineScope, pluginModelFacade: PluginModelFacade, enable: Boolean, callback: (List<PluginUiModel>) -> Unit) {
    coroutineScope.launch(Dispatchers.EDT + ModalityState.any().asContextElement()) {
      val models = mutableListOf<PluginUiModel>()
      val group = pluginModelFacade.getModel().downloadedGroup
      if (group == null || group.ui == null) {
        val appInfo = ApplicationInfoEx.getInstanceEx()

        val plugins = withContext(Dispatchers.IO) { UiPluginManager.getInstance().getPlugins() }
        for (descriptor in plugins) {
          if (!appInfo.isEssentialPlugin(descriptor.pluginId) && !descriptor.isBundled && descriptor.isEnabled != enable) {
            models.add(descriptor)
          }
        }
      }
      else {
        for (component in group.ui.plugins) {
          val plugin: PluginUiModel = component.pluginModel
          if (pluginModelFacade.isEnabled(plugin) != enable) {
            models.add(plugin)
          }
        }
      }
      callback(models)
    }
  }
}
