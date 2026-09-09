// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.newui

import com.intellij.ide.plugins.marketplace.PluginSearchResult
import com.intellij.ide.plugins.marketplace.SetEnabledStateResult
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.application.ex.ApplicationInfoEx
import com.intellij.openapi.components.service
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.platform.ide.CoreUiCoroutineScopeHolder
import com.intellij.util.concurrency.annotations.RequiresEdt
import fleet.rpc.client.RpcClientDisconnectedException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import java.util.function.Consumer
import java.util.function.Function
import javax.swing.JComponent
import kotlin.coroutines.CoroutineContext

internal class PluginOperationUiBridge {
  private val handles = mutableSetOf<PluginOperationUiHandle>()

  @RequiresEdt
  fun createHandle(parentComponent: JComponent): PluginOperationUiHandle {
    return PluginOperationUiHandle(parentComponent) { handle ->
      handles.remove(handle)
    }.also(handles::add)
  }

  @RequiresEdt
  fun detach() {
    handles.toList().forEach(PluginOperationUiHandle::detach)
  }
}

internal class PluginOperationUiHandle(
  parentComponent: JComponent,
  private val onDetached: (PluginOperationUiHandle) -> Unit = {},
) {
  private var parentComponent: JComponent? = parentComponent
  private var detached = false

  @RequiresEdt
  fun captureContext(modalityComponent: JComponent? = parentComponent): PluginOperationUiContext {
    if (modalityComponent == null) return PluginOperationUiContext(ModalityState.defaultModalityState()) { null }
    val modalityState = ModalityState.stateForComponent(modalityComponent)
    return PluginOperationUiContext(modalityState, ::getParentComponent)
  }

  @RequiresEdt
  fun detach() {
    if (detached) return
    detached = true
    parentComponent = null
    onDetached(this)
  }

  @RequiresEdt
  private fun getParentComponent(): JComponent? = parentComponent
}

internal class PluginOperationUiContext(
  val modalityState: ModalityState,
  private val parentComponent: () -> JComponent?,
) {
  fun getParentComponent(): JComponent? = parentComponent()
}

internal class PluginOperationLauncher(
  private val coroutineScope: CoroutineScope,
  private val onOperationSubmitted: () -> Unit = {},
  private val onOperationCompleted: () -> Unit = {},
) {
  fun launch(context: CoroutineContext, operation: suspend CoroutineScope.() -> Unit): Job {
    onOperationSubmitted()
    val job = try {
      coroutineScope.launch(context, block = operation)
    }
    catch (t: Throwable) {
      onOperationCompleted()
      throw t
    }
    job.invokeOnCompletion { onOperationCompleted() }
    return job
  }
}

internal object PluginModelAsyncOperationsExecutor {
  fun performAutoInstall(
    operationLauncher: PluginOperationLauncher,
    modelFacade: PluginModelFacade,
    descriptor: PluginUiModel,
    customizer: PluginManagerCustomizer?,
    operationUi: PluginOperationUiContext,
  ) {
    operationLauncher.launch(Dispatchers.IO) {
      val pluginUpdateSourceApplier = PluginUpdateSourceApplier.createApplier(descriptor, modelFacade)
      pluginUpdateSourceApplier.runWithRevertOnException {
        val customizationModel = customizer?.getInstallButonCustomizationModel(modelFacade, descriptor, operationUi.modalityState)
        withContext(Dispatchers.EDT + operationUi.modalityState.asContextElement()) {
          val customAction = customizationModel?.mainAction
          if (customAction != null) {
            customAction()
            return@withContext
          }
          val result = modelFacade.installOrUpdatePlugin(operationUi, descriptor, null)
          pluginUpdateSourceApplier.applyPluginUpdateSourcesBasedOnResult(result)
        }
      }
    }
  }

  suspend fun performMarketplaceSearch(
    query: String,
  ): PluginSearchResult {
    return withContext(Dispatchers.IO) {
      val pluginManager = UiPluginManager.getInstance()
      pluginManager.executeMarketplaceQuery(query, 10000, true)
    }
  }

  suspend fun getCustomRepositoriesPluginMap(): Map<String, List<PluginUiModel>> {
    return withContext(Dispatchers.IO) {
      val pluginManager = UiPluginManager.getInstance()
      pluginManager.getCustomRepositoryPluginMap()
    }
  }

  suspend fun loadUpdates(): List<PluginUiModel> {
    return withContext(Dispatchers.IO) {
      PluginUpdatesService.getInstance().awaitUpdates().toList()
    }
  }

  @JvmOverloads
  @ApiStatus.Internal
  fun updateErrors(cs: CoroutineScope = service<FrontendRpcCoroutineContext>().coroutineScope, sessionId: String, pluginId: PluginId, callback: (List<HtmlChunk>) -> Unit) {
    cs.launch(Dispatchers.IO) {
      try {
        val errors = UiPluginManager.getInstance().getErrors(sessionId, pluginId)
        val htmlChunks = MyPluginModel.getErrors(errors)
        withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
          callback(htmlChunks)
        }
      }
      catch (_: RpcClientDisconnectedException) {
        // Error refresh can race with remote plugin manager disconnect.
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
      try {
        val result = UiPluginManager.getInstance().enablePlugins(sessionId, descriptorIds, enable, project)
        withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
          PluginManagerCustomizer.getInstance()?.updateAfterModificationAsync { }
          callback(result)
        }
      }
      catch (_: RpcClientDisconnectedException) {
        // Plugin state update can race with remote plugin manager disconnect.
      }
    }
  }

  fun updatePlugin(
    operationLauncher: PluginOperationLauncher,
    modelFacade: PluginModelFacade,
    plugin: PluginUiModel,
    updateDescriptor: PluginUiModel?,
    pluginManagerCustomizer: PluginManagerCustomizer?,
    operationUi: PluginOperationUiContext,
    pluginDescriptorForPluginUpdateSourceApplier: PluginUiModel,
  ): Job {
    return operationLauncher.launch(Dispatchers.IO) {
      val pluginUpdateSourceApplier = PluginUpdateSourceApplier.createApplier(pluginDescriptorForPluginUpdateSourceApplier, modelFacade)
      pluginUpdateSourceApplier.runWithRevertOnException {
        val model = pluginManagerCustomizer?.getUpdateButtonCustomizationModel(
          modelFacade, plugin, updateDescriptor, operationUi.modalityState
        )
        withContext(Dispatchers.EDT + operationUi.modalityState.asContextElement()) {
          if (model != null) {
            model.action()
          }
          else {
            val result = modelFacade.installOrUpdatePlugin(operationUi, plugin, updateDescriptor)
            pluginUpdateSourceApplier.applyPluginUpdateSourcesBasedOnResult(result)
          }
        }
      }
    }
  }

  fun loadPopupMenuActions(
    component: ListPluginComponent,
    selection: List<ListPluginComponent>,
    callback: Consumer<List<AnAction>>,
  ) {
    val customizer = component.getCustomizer()
    if (customizer == null) {
      callback.accept(emptyList())
      return
    }
    val modelFacade = component.getModelFacade()
    component.getUiCoroutineScope().launch(Dispatchers.IO) {
      val stateForComponent = ModalityState.stateForComponent(component)
      val popupSelection = selection.map {
        PluginPopupMenuActionData(it.getPluginModel(), it.getInstalledDescriptorForMarketplace(), it.getDescriptorForActions())
      }
      val popupActions = customizer.getPopupMenuActions(modelFacade, popupSelection, stateForComponent)
      withContext(Dispatchers.EDT + stateForComponent.asContextElement()) {
        callback.accept(popupActions)
      }
    }
  }

  fun findPlugins(pluginIds: Collection<PluginId>, callback: Function<Map<PluginId, PluginUiModel>, Unit>) {
    val coroutineScope = service<CoreUiCoroutineScopeHolder>().coroutineScope
    coroutineScope.launch(Dispatchers.IO) {
      try {
        val pluginModels = UiPluginManager.getInstance().findInstalledPlugins(pluginIds.toSet())
        withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
          callback.apply(pluginModels)
        }
      }
      catch (_: RpcClientDisconnectedException) {
        // The remote plugin manager may be gone before this UI refresh completes.
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
      try {
        val installationState = UiPluginManager.getInstance().getPluginInstallationState(pluginId)
        val installedDescriptor = if (isMarketplace && installationState.status != PluginStatus.INSTALLED_AND_REQUIRED_RESTART) {
          UiPluginManager.getInstance().getPlugin(pluginId)
        }
        else null
        withContext(Dispatchers.EDT + ModalityState.stateForComponent(component).asContextElement()) {
          callback(installationState, installedDescriptor)
        }
      }
      catch (_: RpcClientDisconnectedException) {
        // Button refresh can race with remote plugin manager disconnect.
      }
    }
  }

  fun switchPlugins(coroutineScope: CoroutineScope, pluginModelFacade: PluginModelFacade, enable: Boolean, callback: (List<PluginUiModel>) -> Unit) {
    coroutineScope.launch(Dispatchers.EDT + ModalityState.any().asContextElement()) {
      try {
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
          for (component in group.ui!!.plugins) {
            val plugin: PluginUiModel = component.getPluginModel()
            if (pluginModelFacade.isEnabled(plugin) != enable) {
              models.add(plugin)
            }
          }
        }
        callback(models)
      }
      catch (_: RpcClientDisconnectedException) {
        // Bulk plugin switch refresh can race with remote plugin manager disconnect.
      }
    }
  }
}
