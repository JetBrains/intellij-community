// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.newui

import com.intellij.ide.plugins.marketplace.PluginSearchResult
import com.intellij.ide.plugins.marketplace.SetEnabledStateResult
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.application.ex.ApplicationInfoEx
import com.intellij.openapi.components.service
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.Project
import com.intellij.openapi.updateSettings.impl.PluginDownloader
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.platform.ide.CoreUiCoroutineScopeHolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import java.util.function.Function
import javax.swing.JComponent

internal object PluginModelAsyncOperationsExecutor {
  fun performAutoInstall(
    cs: CoroutineScope,
    modelFacade: PluginModelFacade,
    descriptor: PluginUiModel,
    customizer: PluginManagerCustomizer?,
    component: JComponent,
  ) {
    cs.launch(Dispatchers.IO) {
      val stateForComponent = ModalityState.stateForComponent(component)
      val customizationModel = customizer?.getInstallButonCustomizationModel(modelFacade, descriptor, stateForComponent)
      withContext(Dispatchers.EDT + stateForComponent.asContextElement()) {
        val customAction = customizationModel?.mainAction
        if (customAction != null) {
          customAction()
          return@withContext
        }
        modelFacade.installOrUpdatePlugin(component, descriptor, null, stateForComponent)
      }
    }
  }

  fun performMarketplaceSearch(
    cs: CoroutineScope,
    query: String,
    loadUpdates: Boolean,
    callback: (PluginSearchResult, List<PluginUiModel>) -> Unit,
  ) {
    cs.launch(Dispatchers.IO) {
      val pluginManager = UiPluginManager.getInstance()
      val result = pluginManager.executeMarketplaceQuery(query, 10000, true)
      val updates = mutableListOf<PluginUiModel>()
      if (loadUpdates) {
        updates.addAll(pluginManager.getUpdateModels())
      }
      callback(result, updates)
    }
  }

  fun getCustomRepositoriesPluginMap(cs: CoroutineScope, callback: (Map<String, List<PluginUiModel>>) -> Unit) {
    cs.launch(Dispatchers.IO) {
      val pluginManager = UiPluginManager.getInstance()
      val result = pluginManager.getCustomRepositoryPluginMap()
      callback(result)
    }
  }

  fun loadUpdates(cs: CoroutineScope, callback: (List<PluginUiModel>) -> Unit) {
    cs.launch(Dispatchers.IO) {
      val updates = UiPluginManager.getInstance().getUpdateModels()
      withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
        callback(updates)
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
        PluginManagerCustomizer.getInstance()?.updateAfterModificationAsync { }
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

  fun findPlugins(downloaders: Collection<PluginDownloader>, callback: Function<Map<PluginId, PluginUiModel>, Unit>) {
    val coroutineScope = service<CoreUiCoroutineScopeHolder>().coroutineScope
    coroutineScope.launch(Dispatchers.IO) {
      val pluginModels = UiPluginManager.getInstance().findInstalledPlugins(downloaders.map(PluginDownloader::getId).toSet())
      withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
        callback.apply(pluginModels)
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
      withContext(Dispatchers.EDT + ModalityState.stateForComponent(component).asContextElement()) {
        callback(installationState, installedDescriptor)
      }
    }
  }

  fun switchPlugins(coroutineScope: CoroutineScope, pluginModelFacade: PluginModelFacade, enable: Boolean, callback: (List<PluginUiModel>) -> Unit) {
    coroutineScope.launch(Dispatchers.EDT + ModalityState.any().asContextElement()) {
      val models = mutableListOf<PluginUiModel>()
      val group = pluginModelFacade.getModel().userInstalled
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
