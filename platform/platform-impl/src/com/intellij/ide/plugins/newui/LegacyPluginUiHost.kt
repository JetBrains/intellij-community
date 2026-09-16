// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.newui

import com.intellij.ide.plugins.ListPluginModel
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.ide.plugins.InstallFromDiskAction
import com.intellij.ide.plugins.PluginInstallCallbackData
import com.intellij.ide.plugins.PluginManagerCoroutineScopeHolder
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.components.service
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.Project
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.updateSettings.impl.PluginUpdateSourceId
import com.intellij.openapi.updateSettings.impl.PluginUpdateSourceService
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.FUSEventSource
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.platform.util.coroutines.childScope
import com.intellij.ui.components.labels.LinkListener
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.text.VersionComparatorUtil
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import java.awt.Component
import java.util.Collections
import java.util.IdentityHashMap
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import java.util.function.Consumer
import javax.swing.JComponent
import kotlin.coroutines.CoroutineContext

@ApiStatus.Internal
class LegacyPluginUiHost @RequiresEdt(generateAssertion = false /* IJPL-115548 */) constructor(
  project: Project? = null,
  parentScope: CoroutineScope = service<PluginManagerCoroutineScopeHolder>().coroutineScope,
  operationScope: CoroutineScope = service<PluginManagerCoroutineScopeHolder>().coroutineScope,
  unifiedDetailsPageLayout: Boolean = false,
  private val pluginIconScale: Float = 1.0f,
) {
  private val detailsPageLayout = if (unifiedDetailsPageLayout) {
    PluginDetailsPageLayout.Unified
  }
  else {
    PluginDetailsPageLayout.Legacy
  }
  private val eventSink = LegacyPluginUiHostEventSink()
  private val repositoryPlugins = AtomicReference<Map<String, List<PluginUiModel>>>(emptyMap())
  private val uiScope = parentScope.childScope(javaClass.name, Dispatchers.IO, true)
  private val model = LegacyPluginUiModel(project, eventSink) { repositoryPlugins.get() }
  private val operationUiBridge = PluginOperationUiBridge()
  private val operationLauncher = PluginOperationLauncher(
    operationScope,
    eventSink::operationLaunchSubmitted,
    eventSink::operationLaunchCompleted,
  )
  private val facade = PluginModelFacade(model).also { it.setOperationLauncher(operationLauncher) }
  private val applyBridge = PluginApplyCallbackBridge(operationScope, model::applyAsync)
  private val topController = LegacyPluginTopControllerBridge()
  private val rows: MutableSet<ListPluginComponent> = Collections.newSetFromMap(IdentityHashMap())
  private val details: MutableSet<PluginDetailsPageComponent> = Collections.newSetFromMap(IdentityHashMap())
  private val lifecycle = LegacyPluginUiHostLifecycle(
    isModified = model::isModified,
    apply = { parentComponent, successCallback, failureCallback ->
      applyBridge.submit(parentComponent, successCallback, failureCallback)
    },
    reset = model::clear,
    cancel = { parentComponent -> model.cancel(parentComponent, true) },
    toBackground = model::toBackground,
    detachTopController = topController::detach,
    detachOperationUi = operationUiBridge::detach,
    detachDetails = ::detachAllDetails,
    closeRows = ::closeAllRows,
    clearCallbacks = {
      model.setInvalidFixCallback(null)
      model.clearCancelInstallCallback()
      repositoryPlugins.set(emptyMap())
    },
    cancelUiWork = {
      eventSink.closeEvents()
      uiScope.cancel()
    },
  )

  init {
    model.coroutineScope = uiScope
    model.setTopController(topController)
    eventSink.setCloseSessionAction(facade::closeSession)
  }

  val events: Flow<PluginModelEvent>
    get() = eventSink.events

  val sessionId: String
    get() = model.sessionId

  val createShutdownCallback: Boolean
    get() = model.createShutdownCallback

  suspend fun awaitSessionInitialization() {
    lifecycle.checkActive()
    model.waitForSessionInitialization()
  }

  suspend fun getPluginEnabledStates(plugins: Collection<PluginUiModel>): Map<PluginId, Boolean> {
    awaitSessionInitialization()
    return facade.getEnabledStatesSnapshot(plugins)
  }

  @RequiresEdt(generateAssertion = false /* IJPL-115548 */)
  fun setTopController(controller: Configurable.TopComponentController) {
    lifecycle.checkActive()
    topController.attach(controller)
    model.setTopController(topController)
  }

  fun setInstallSource(source: FUSEventSource?) {
    lifecycle.checkActive()
    model.setInstallSource(source)
  }

  fun updateCustomRepositoryPlugins(plugins: Map<String, List<PluginUiModel>>) {
    lifecycle.checkActive()
    repositoryPlugins.set(plugins)
  }

  @RequiresEdt(generateAssertion = false /* IJPL-115548 */)
  fun createInstallFromDiskAction(
    parentComponent: JComponent,
    beforeAction: () -> Unit,
    onPluginInstalled: (PluginInstallCallbackData) -> Unit,
  ): InstallFromDiskAction {
    lifecycle.checkActive()
    return object : InstallFromDiskAction(model, model, parentComponent) {
      override fun actionPerformed(event: AnActionEvent) {
        beforeAction()
        super.actionPerformed(event)
      }

      override fun onPluginInstalledFromDisk(callbackData: PluginInstallCallbackData, project: Project?) {
        onPluginInstalled(callbackData)
      }

      override fun onPluginWithUpdateSourceInstalledFromDisk(pluginId: PluginId, updateSourceId: PluginUpdateSourceId) {
        PluginUpdateSourceService.getInstance().setPluginUpdateSourceId(pluginId, updateSourceId)
      }
    }
  }

  @RequiresEdt(generateAssertion = false /* IJPL-115548 */)
  fun completeInstallFromDisk(
    callbackData: PluginInstallCallbackData,
    errors: List<HtmlChunk>,
    source: PluginSource,
  ) {
    lifecycle.checkActive()
    model.pluginInstalledFromDisk(callbackData, errors, source)
  }

  @RequiresEdt(generateAssertion = false /* IJPL-115548 */)
  fun enable(descriptors: Set<IdeaPluginDescriptor>) {
    lifecycle.checkActive()
    model.enable(descriptors)
  }

  fun changeAllPluginsState(enable: Boolean, installedPlugins: List<PluginUiModel>) {
    lifecycle.checkActive()
    PluginModelAsyncOperationsExecutor.switchPlugins(uiScope, facade, installedPlugins.toList(), enable) { models ->
      val restrictedPluginIds = UiPluginManager.getInstance().filterPluginsRequiringUltimateButItsDisabled(models.map { it.pluginId })
      val suitableModels = models.filter { it.pluginId !in restrictedPluginIds }
      if (enable) {
        facade.enable(suitableModels)
      }
      else {
        facade.disable(suitableModels)
      }
    }
  }

  @RequiresEdt(generateAssertion = false /* IJPL-115548 */)
  internal fun createPluginUpdateOperationContext(update: PluginUiModel): PluginOperationContext {
    lifecycle.checkActive()
    return PluginOperationContext.create(
      displayPluginId = update.pluginId,
      target = UiPluginManager.getInstance().getController().getTarget(),
      kind = PluginOperationKind.UPDATE,
    )
  }

  @RequiresEdt(generateAssertion = false /* IJPL-115548 */)
  internal fun startPluginUpdate(
    installedPlugin: PluginUiModel,
    update: PluginUiModel,
    parentComponent: JComponent,
    operationContext: PluginOperationContext,
  ) {
    lifecycle.checkActive()
    val operationUiHandle = operationUiBridge.createHandle(parentComponent)
    val operationJob = PluginModelAsyncOperationsExecutor.updatePlugin(
      operationLauncher,
      facade,
      installedPlugin,
      update,
      PluginManagerCustomizer.getInstance(),
      operationUiHandle.captureContext(parentComponent),
      update,
      operationContext = operationContext,
    )
    operationJob.invokeOnCompletion {
      uiScope.launch(Dispatchers.EDT + ModalityState.any().asContextElement()) {
        operationUiHandle.detach()
      }
    }
  }

  @RequiresEdt(generateAssertion = false /* IJPL-115548 */)
  fun requestRestart(customizer: PluginManagerCustomizer, parentComponent: JComponent) {
    customizer.requestRestart(facade, parentComponent)
  }

  fun closeSession() {
    eventSink.requestSessionClose(installationMovedToBackground = false)
  }

  @RequiresEdt(generateAssertion = false /* IJPL-115548 */)
  internal fun createRowRenderKey(
    plugin: PluginUiModel,
    group: PluginsGroup,
    listModel: ListPluginModel,
    marketplace: Boolean,
  ): PluginRowRenderKey {
    lifecycle.checkActive()
    return ListPluginComponent.createRenderKey(facade, plugin, group, listModel, marketplace)
  }

  @RequiresEdt(generateAssertion = false /* IJPL-115548 */)
  internal fun createRowRenderKey(
    plugin: PluginUiModel,
    group: PluginsGroup,
    marketplace: Boolean,
    input: PluginRowInput,
  ): PluginRowRenderKey {
    lifecycle.checkActive()
    return ListPluginComponent.createRenderKey(
      plugin = plugin,
      installedPlugin = input.installedPlugin,
      installationState = input.installationState,
      groupType = group.type,
      marketplace = marketplace,
      pluginEnabled = input.enabled,
      restrictedByProduct = input.restrictedByProduct,
      listCustomizerClassName = getListPluginComponentCustomizer().javaClass.name,
      pluginManagerCustomizerClassName = PluginManagerCustomizer.getInstance()?.javaClass?.name,
      preparedUpdate = input.preparedUpdate,
    )
  }

  @RequiresEdt(generateAssertion = false /* IJPL-115548 */)
  fun createRow(
    plugin: PluginUiModel,
    group: PluginsGroup,
    listModel: ListPluginModel,
    searchListener: LinkListener<Any>,
    marketplace: Boolean,
  ): ListPluginComponent {
    lifecycle.checkActive()
    return ListPluginComponent(
      facade,
      plugin,
      group,
      listModel,
      searchListener,
      uiScope,
      operationLauncher,
      operationUiBridge,
      marketplace,
      secondaryButtons = true,
      badgeTags = true,
      islandSelection = true,
      toggleForEnablement = true,
      pluginIconScale = pluginIconScale,
    ).also(rows::add)
  }

  @RequiresEdt(generateAssertion = false /* IJPL-115548 */)
  internal fun createRow(
    plugin: PluginUiModel,
    group: PluginsGroup,
    listModel: ListPluginModel,
    searchListener: LinkListener<Any>,
    marketplace: Boolean,
    renderKey: PluginRowRenderKey,
    registerInstallingWithoutGroup: Boolean,
  ): ListPluginComponent {
    lifecycle.checkActive()
    return ListPluginComponent(
      facade,
      plugin,
      group,
      listModel,
      searchListener,
      uiScope,
      operationLauncher,
      operationUiBridge,
      marketplace,
      renderKey,
      registerInstallingWithoutGroup,
      secondaryButtons = true,
      badgeTags = true,
      islandSelection = true,
      toggleForEnablement = true,
      pluginIconScale = pluginIconScale,
    ).also(rows::add)
  }

  @RequiresEdt(generateAssertion = false /* IJPL-115548 */)
  fun releaseRow(row: ListPluginComponent) {
    if (rows.remove(row)) {
      row.close()
    }
  }

  @RequiresEdt(generateAssertion = false /* IJPL-115548 */)
  fun createDetails(
    searchListener: LinkListener<Any>,
    marketplace: Boolean,
    customizationStrategy: PluginDetailsPageCustomizationStrategy = DefaultPluginDetailsPageCustomizationStrategy,
  ): PluginDetailsPageComponent {
    lifecycle.checkActive()
    val component = PluginDetailsPageComponent(
      facade,
      searchListener,
      marketplace,
      customizationStrategy,
      operationLauncher,
      operationUiBridge,
      secondaryButtons = true,
      badgeTags = true,
      layout = detailsPageLayout,
    )
    model.addDetailPanel(component)
    details.add(component)
    return component
  }

  @RequiresEdt(generateAssertion = false /* IJPL-115548 */)
  fun releaseDetails(component: PluginDetailsPageComponent) {
    if (details.remove(component)) {
      component.detach()
    }
  }

  fun isModified(): Boolean = lifecycle.isModified()

  fun apply(
    parentComponent: JComponent?,
    successCallback: Consumer<Boolean>,
    failureCallback: Consumer<Throwable>,
  ) {
    lifecycle.apply(parentComponent, successCallback, failureCallback)
  }

  fun reset(parentComponent: JComponent?) {
    lifecycle.reset(parentComponent)
  }

  fun cancel(parentComponent: JComponent?): Job = lifecycle.cancel(parentComponent)

  /**
   * Detaches all page UI. [closeSession] must be false when reset with session removal or restart handling owns the backend session.
   */
  @RequiresEdt(generateAssertion = false /* IJPL-115548 */)
  fun dispose(closeSession: Boolean): Boolean {
    val installationMovedToBackground = lifecycle.disposeUi()
    if (closeSession) {
      eventSink.requestSessionClose(installationMovedToBackground)
    }
    return installationMovedToBackground
  }

  private fun detachAllDetails() {
    details.toList().forEach(PluginDetailsPageComponent::detach)
    details.clear()
  }

  private fun closeAllRows() {
    rows.toList().forEach(ListPluginComponent::close)
    rows.clear()
  }
}

private class LegacyPluginUiModel(
  project: Project?,
  eventSink: PluginModelEventSink,
  private val repositoryPlugins: () -> Map<String, List<PluginUiModel>>,
) : MyPluginModel(project, eventSink) {
  override fun customRepoPluginsFor(target: PluginSource, plugin: PluginUiModel): Collection<PluginUiModel> {
    return selectCustomRepositoryPluginsForInstall(repositoryPlugins(), plugin, target)
  }
}

internal fun selectCustomRepositoryPluginsForInstall(
  repositories: Map<String, List<PluginUiModel>>,
  plugin: PluginUiModel,
  target: PluginSource,
): List<PluginUiModel> {
  val sourcePlugins = plugin.repositoryName?.let(repositories::get) ?: combinedCustomRepositoryPlugins(repositories)
  val targetPlugins = filterCustomRepositoryPluginsForTarget(sourcePlugins, target).orEmpty()
  return latestCustomRepositoryPlugins(targetPlugins)
}

private fun combinedCustomRepositoryPlugins(repositories: Map<String, List<PluginUiModel>>): List<PluginUiModel> {
  return latestCustomRepositoryPlugins(repositories.values.flatten())
}

internal fun latestCustomRepositoryPlugins(plugins: Iterable<PluginUiModel>): List<PluginUiModel> {
  val latest = LinkedHashMap<PluginId, PluginUiModel>()
  plugins.forEach { plugin ->
    val current = latest[plugin.pluginId]
    if (current == null || VersionComparatorUtil.compare(plugin.version, current.version) > 0) {
      latest[plugin.pluginId] = plugin
    }
  }
  return latest.values.toList()
}

internal fun filterCustomRepositoryPluginsForTarget(
  plugins: Collection<PluginUiModel>?,
  target: PluginSource,
): List<PluginUiModel>? {
  return plugins?.filter { plugin ->
    when (target) {
      PluginSource.LOCAL -> plugin.source?.isLocal() != false
      PluginSource.REMOTE -> plugin.source?.isRemote() != false
      PluginSource.BOTH -> true
    }
  }
}

internal class LegacyPluginTopControllerBridge : Configurable.TopComponentController {
  @Volatile
  private var delegate: Configurable.TopComponentController = Configurable.TopComponentController.EMPTY

  fun attach(controller: Configurable.TopComponentController) {
    delegate = controller
  }

  fun detach() {
    delegate = Configurable.TopComponentController.EMPTY
  }

  override fun setLeftComponent(component: Component?) {
    delegate.setLeftComponent(component)
  }

  override fun showProgress(start: Boolean) {
    delegate.showProgress(start)
  }

  override fun showProject(hasProject: Boolean) {
    delegate.showProject(hasProject)
  }
}

internal class LegacyPluginUiHostLifecycle(
  private val isModified: () -> Boolean,
  private val apply: (JComponent?, Consumer<Boolean>, Consumer<Throwable>) -> Unit,
  private val reset: (JComponent?) -> Unit,
  private val cancel: (JComponent?) -> Job,
  private val toBackground: () -> Boolean,
  private val detachTopController: () -> Unit,
  private val detachOperationUi: () -> Unit,
  private val detachDetails: () -> Unit,
  private val closeRows: () -> Unit,
  private val clearCallbacks: () -> Unit,
  private val cancelUiWork: () -> Unit,
) {
  private var disposed = false
  private var installationMovedToBackground = false

  fun checkActive() {
    check(!disposed) { "Legacy plugin UI host is disposed" }
  }

  fun isModified(): Boolean {
    checkActive()
    return isModified.invoke()
  }

  fun apply(
    parentComponent: JComponent?,
    successCallback: Consumer<Boolean>,
    failureCallback: Consumer<Throwable>,
  ) {
    checkActive()
    apply.invoke(parentComponent, successCallback, failureCallback)
  }

  fun reset(parentComponent: JComponent?) {
    checkActive()
    reset.invoke(parentComponent)
  }

  fun cancel(parentComponent: JComponent?): Job {
    checkActive()
    return cancel.invoke(parentComponent)
  }

  fun disposeUi(): Boolean {
    if (disposed) return installationMovedToBackground
    disposed = true

    installationMovedToBackground = toBackground()
    detachTopController()
    detachOperationUi()
    detachDetails()
    closeRows()
    clearCallbacks()
    cancelUiWork()
    return installationMovedToBackground
  }
}

internal class PluginApplyCallbackBridge(
  private val operationScope: CoroutineScope,
  private val apply: suspend (JComponent?) -> Boolean,
  private val callbackContext: (JComponent?) -> CoroutineContext = { parentComponent ->
    val modality = parentComponent?.let(ModalityState::stateForComponent) ?: ModalityState.defaultModalityState()
    Dispatchers.EDT + modality.asContextElement()
  },
) {
  fun submit(
    parentComponent: JComponent?,
    successCallback: Consumer<Boolean>,
    failureCallback: Consumer<Throwable>,
  ): Job {
    val completionContext = callbackContext(parentComponent)
    return operationScope.launch(CoroutineName("Plugins application")) {
      val installedWithoutRestart = try {
        apply(parentComponent)
      }
      catch (c: CancellationException) {
        withContext(completionContext + NonCancellable) {
          failureCallback.accept(c)
        }
        throw c
      }
      catch (t: Throwable) {
        withContext(completionContext) {
          failureCallback.accept(t)
        }
        return@launch
      }

      withContext(completionContext) {
        successCallback.accept(installedWithoutRestart)
      }
    }
  }
}

internal class LegacyPluginUiHostEventSink : PluginModelEventSink {
  private val eventChannel = Channel<PluginModelEvent>(Channel.UNLIMITED)
  private val activeOperations = mutableSetOf<UUID>()
  private var pendingOperationLaunches = 0
  private var closeSessionAction: (() -> Unit)? = null
  private var closeSessionRequested = false
  private var sessionClosed = false
  private var uncorrelatedBackgroundOperation = false

  val events: Flow<PluginModelEvent> = eventChannel.receiveAsFlow()

  fun setCloseSessionAction(action: () -> Unit) {
    synchronized(activeOperations) {
      check(closeSessionAction == null)
      closeSessionAction = action
    }
  }

  override fun onEvent(event: PluginModelEvent) {
    val closeAction = synchronized(activeOperations) {
      when (event) {
        is PluginModelEvent.OperationStarted -> {
          activeOperations.add(event.operationId)
          uncorrelatedBackgroundOperation = false
        }
        is PluginModelEvent.OperationFinished -> {
          activeOperations.remove(event.operationId)
          uncorrelatedBackgroundOperation = false
        }
        is PluginModelEvent.OperationDependenciesScheduled -> Unit
        is PluginModelEvent.InventoryInvalidated -> Unit
      }
      closeSessionIfReady()
    }
    eventChannel.trySend(event)
    closeAction?.invoke()
  }

  fun requestSessionClose(installationMovedToBackground: Boolean) {
    val closeAction = synchronized(activeOperations) {
      closeSessionRequested = true
      uncorrelatedBackgroundOperation = installationMovedToBackground && activeOperations.isEmpty() && pendingOperationLaunches == 0
      closeSessionIfReady()
    }
    closeAction?.invoke()
  }

  fun operationLaunchSubmitted() {
    synchronized(activeOperations) {
      pendingOperationLaunches++
    }
  }

  fun operationLaunchCompleted() {
    val closeAction = synchronized(activeOperations) {
      if (pendingOperationLaunches > 0) {
        pendingOperationLaunches--
      }
      closeSessionIfReady()
    }
    closeAction?.invoke()
  }

  fun closeEvents() {
    eventChannel.close()
  }

  private fun closeSessionIfReady(): (() -> Unit)? {
    if (!closeSessionRequested || sessionClosed || activeOperations.isNotEmpty() || pendingOperationLaunches > 0 ||
        uncorrelatedBackgroundOperation) {
      return null
    }
    val closeAction = checkNotNull(closeSessionAction)
    sessionClosed = true
    return closeAction
  }
}
