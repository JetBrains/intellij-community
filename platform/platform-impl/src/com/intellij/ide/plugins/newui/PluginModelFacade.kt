// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.newui

import com.intellij.ide.plugins.PluginEnableDisableAction
import com.intellij.ide.plugins.PluginEnabledState
import com.intellij.ide.plugins.PluginManagerCoroutineScopeHolder
import com.intellij.ide.plugins.marketplace.InstallPluginResult
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.components.service
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.updateSettings.impl.PluginUpdateSourceId
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.openapi.wm.ex.ProgressIndicatorEx
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import javax.swing.JComponent
import kotlin.coroutines.CoroutineContext

@ApiStatus.Internal
open class PluginModelFacade(private val pluginModel: MyPluginModel) {
  private var operationLauncher: PluginOperationLauncher? = null

  internal fun setOperationLauncher(operationLauncher: PluginOperationLauncher) {
    this.operationLauncher = operationLauncher
  }

  @ApiStatus.Internal
  fun launchOperation(context: CoroutineContext, operation: suspend CoroutineScope.() -> Unit) {
    getOperationLauncher().launch(context, operation)
  }

  @ApiStatus.Internal
  fun updatePlugin(
    plugin: PluginUiModel,
    updateDescriptor: PluginUiModel,
    modalityState: ModalityState,
    operationContext: PluginOperationContext,
  ) {
    PluginModelAsyncOperationsExecutor.updatePlugin(
      getOperationLauncher(),
      this,
      plugin,
      updateDescriptor,
      PluginManagerCustomizer.getInstance(),
      PluginOperationUiContext.withoutParent(modalityState),
      updateDescriptor,
      operationContext = operationContext,
    )
  }

  private fun getOperationLauncher(): PluginOperationLauncher =
    operationLauncher ?: PluginOperationLauncher(service<PluginManagerCoroutineScopeHolder>().coroutineScope)

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
  suspend fun installOrUpdatePlugin(
    component: JComponent?,
    model: PluginUiModel,
    updateDescriptor: PluginUiModel?,
    modalityState: ModalityState,
    controller: UiPluginManagerController = UiPluginManager.getInstance().getController(),
    operationContext: PluginOperationContext? = null,
  ): InstallPluginResult? {
    val operationUi = PluginOperationUiContext(modalityState) { component }
    return installOrUpdatePlugin(operationUi, model, updateDescriptor, controller, operationContext)
  }

  internal suspend fun installOrUpdatePlugin(
    operationUi: PluginOperationUiContext,
    model: PluginUiModel,
    updateDescriptor: PluginUiModel?,
    controller: UiPluginManagerController = UiPluginManager.getInstance().getController(),
    operationContext: PluginOperationContext? = null,
  ): InstallPluginResult? {
    val target = controller.getTarget()
    val context = operationContext ?: PluginOperationContext.create(
      displayPluginId = model.pluginId,
      target = target,
      kind = if (updateDescriptor == null) PluginOperationKind.INSTALL else PluginOperationKind.UPDATE,
    )
    val finishOperation = operationContext == null
    pluginModel.operationStarted(context, updateDescriptor ?: model)
    try {
      val installTask = service<PluginManagerCoroutineScopeHolder>().coroutineScope.async(
        CoroutineName("Install plugin ${model.pluginId}")
      ) {
        pluginModel.installOrUpdatePlugin(
          operationUi, model, updateDescriptor, this, controller,
          PluginInstallationProgressSink { dependencies ->
            pluginModel.operationDependenciesScheduled(context, dependencies)
          },
        )
      }
      val result = withContext(NonCancellable) { installTask.await() }
      pluginModel.operationTargetFinished(
        context,
        target,
        result.toTerminalResult(),
        result?.installedDependencyDescriptors.orEmpty(),
        result?.restartRequired == true,
      )
      return result
    }
    catch (e: CancellationException) {
      pluginModel.operationTargetFinished(context, target, PluginOperationTerminalResult.CANCELLED)
      throw e
    }
    catch (t: Throwable) {
      pluginModel.operationTargetFinished(context, target, PluginOperationTerminalResult.FAILED)
      throw t
    }
    finally {
      if (finishOperation) {
        pluginModel.operationFinished(context)
      }
    }
  }

  fun startOperation(context: PluginOperationContext, presentationModel: PluginUiModel) {
    pluginModel.operationStarted(context, presentationModel)
  }

  fun finishOperation(context: PluginOperationContext) {
    pluginModel.operationFinished(context)
  }

  fun addUninstalled(pluginId: PluginId) {
    pluginModel.addUninstalled(pluginId)
  }

  fun getIcon(model: PluginUiModel, big: Boolean, error: Boolean, disabled: Boolean): javax.swing.Icon {
    return pluginModel.getIcon(model.getDescriptor(), big, error, disabled)
  }

  fun getErrors(model: PluginUiModel): List<HtmlChunk> {
    return pluginModel.getErrorsSync(model.getDescriptor())
  }

  suspend fun enableRequiredPlugins(model: PluginUiModel) {
    pluginModel.enableRequiredPlugins(model.getDescriptor())
  }

  fun enableRequiredPluginsAsync(model: PluginUiModel) {
    pluginModel.coroutineScope.launch(Dispatchers.IO) {
      enableRequiredPlugins(model)
    }
  }

  fun isUninstalled(pluginId: PluginId): Boolean {
    return pluginModel.isUninstalled(pluginId)
  }

  fun isEnabled(model: PluginUiModel): Boolean {
    return pluginModel.isEnabled(model.getDescriptor())
  }

  suspend fun finishInstall(model: PluginUiModel, installedModel: PluginUiModel?, success: Boolean, showErrors: Boolean, restartRequired: Boolean, errors: Map<PluginId, List<HtmlChunk>>) {
    pluginModel.finishInstall(model, installedModel, errors, success, showErrors, restartRequired)
  }

  fun isPluginRequiredForProject(model: PluginUiModel): Boolean {
    return pluginModel.isRequiredPluginForProject(model.pluginId)
  }

  fun addComponent(component: ListPluginComponent, registerInstallingWithoutGroup: Boolean = false) {
    pluginModel.addComponent(component, registerInstallingWithoutGroup)
  }

  fun removeComponent(component: ListPluginComponent) {
    pluginModel.removeComponent(component)
  }

  fun setEnabledState(models: Collection<PluginUiModel>, action: PluginEnableDisableAction) {
    pluginModel.setEnabledStateAsync(models.map { it.getDescriptor() }, action)
  }

  @JvmOverloads
  suspend fun uninstallAndUpdateUi(descriptor: PluginUiModel, controller: UiPluginManagerController = UiPluginManager.getInstance().getController(), callback: () -> Unit = {}) {
    pluginModel.uninstallAndUpdateUi(descriptor, controller, callback)
  }

  suspend fun isDisabledInDiff(model: PluginUiModel): Boolean {
    return UiPluginManager.getInstance().isDisabledInDiff(pluginModel.sessionId, model.pluginId)
  }

  fun isLoaded(model: PluginUiModel): Boolean {
    return pluginModel.isLoaded(model.pluginId)
  }

  suspend fun getPluginUpdateSource(pluginId: PluginId): PluginUpdateSourceId? {
    return UiPluginManager.getInstance().getPluginUpdateSource(pluginModel.sessionId, pluginId)
  }

  suspend fun setPendingPluginUpdateSourceInSession(pluginId: PluginId, pluginUpdateSource: PluginUpdateSourceId?) {
    UiPluginManager.getInstance().setPendingPluginUpdateSourceInSession(pluginModel.sessionId, pluginId, pluginUpdateSource)
  }

  suspend fun persistPluginUpdateSource(pluginId: PluginId, pluginUpdateSource: PluginUpdateSourceId?){
    UiPluginManager.getInstance().persistPluginUpdateSource(pluginModel.sessionId, pluginId, pluginUpdateSource)
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

private fun InstallPluginResult?.toTerminalResult(): PluginOperationTerminalResult {
  return when {
    this == null || cancel -> PluginOperationTerminalResult.CANCELLED
    success -> PluginOperationTerminalResult.SUCCEEDED
    else -> PluginOperationTerminalResult.FAILED
  }
}
