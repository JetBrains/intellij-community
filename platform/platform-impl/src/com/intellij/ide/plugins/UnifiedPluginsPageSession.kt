// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.icons.AllIcons
import com.intellij.ide.CopyProvider
import com.intellij.ide.IdeBundle
import com.intellij.ide.impl.ProjectUtil.getActiveProject
import com.intellij.ide.plugins.marketplace.statistics.PluginManagerUsageCollector
import com.intellij.ide.plugins.marketplace.statistics.UnifiedPluginSearchStatistics
import com.intellij.ide.plugins.marketplace.statistics.enums.PluginManagerOpenSourceEnum
import com.intellij.ide.plugins.marketplace.statistics.enums.UnifiedPluginSearchSourceKind
import com.intellij.ide.plugins.newui.LegacyPluginUiHost
import com.intellij.ide.plugins.newui.PluginManagerCustomizer
import com.intellij.ide.plugins.newui.PluginModelAsyncOperationsExecutor
import com.intellij.ide.plugins.newui.PluginModelEvent
import com.intellij.ide.plugins.newui.PluginPriceService
import com.intellij.ide.plugins.newui.PluginSource
import com.intellij.ide.plugins.newui.PluginUpdatesEvent
import com.intellij.ide.plugins.newui.PluginUpdatesService
import com.intellij.ide.plugins.newui.SearchQueryParser
import com.intellij.ide.plugins.newui.TabbedPaneHeaderComponent
import com.intellij.ide.plugins.newui.TagComponent
import com.intellij.ide.plugins.unified.BundledPluginCategoryAction
import com.intellij.ide.plugins.unified.DefaultUnifiedPluginLocalDataProvider
import com.intellij.ide.plugins.unified.DefaultUnifiedPluginMarketplaceDataProvider
import com.intellij.ide.plugins.unified.DefaultUnifiedPluginRepositoryDataProvider
import com.intellij.ide.plugins.unified.LegacyPluginDetailsPresenter
import com.intellij.ide.plugins.unified.LegacyPluginRowFactory
import com.intellij.ide.plugins.unified.PageSessionPluginUpdateAllExecutor
import com.intellij.ide.plugins.unified.PluginOccurrenceId
import com.intellij.ide.plugins.unified.PluginSectionId
import com.intellij.ide.plugins.unified.PluginSectionStatus
import com.intellij.ide.plugins.unified.PluginsQueryScope
import com.intellij.ide.plugins.unified.PluginsQueryState
import com.intellij.ide.plugins.unified.UnifiedPluginInternalGroup
import com.intellij.ide.plugins.unified.UnifiedPluginInternalSourceCoordinator
import com.intellij.ide.plugins.unified.UnifiedPluginLocalDataProvider
import com.intellij.ide.plugins.unified.UnifiedPluginLocalSourceCoordinator
import com.intellij.ide.plugins.unified.UnifiedPluginMarketplaceDataProvider
import com.intellij.ide.plugins.unified.UnifiedPluginMarketplaceSourceCoordinator
import com.intellij.ide.plugins.unified.UnifiedPluginRepositoryCache
import com.intellij.ide.plugins.unified.UnifiedPluginRepositoryDataProvider
import com.intellij.ide.plugins.unified.UnifiedPluginRepositorySourceCoordinator
import com.intellij.ide.plugins.unified.UnifiedPluginSearchControlIntent
import com.intellij.ide.plugins.unified.UnifiedPluginUpdateAllButton
import com.intellij.ide.plugins.unified.UnifiedPluginUpdateAllController
import com.intellij.ide.plugins.unified.UnifiedPluginUpdateAllExecutor
import com.intellij.ide.plugins.unified.UnifiedPluginUpdateAllIntent
import com.intellij.ide.plugins.unified.UnifiedPluginsPageActions
import com.intellij.ide.plugins.unified.UnifiedPluginsPageController
import com.intellij.ide.plugins.unified.UnifiedPluginsPageSourceCoordinator
import com.intellij.ide.plugins.unified.UnifiedPluginsPageSourceState
import com.intellij.ide.plugins.unified.UnifiedPluginsPageView
import com.intellij.ide.plugins.unified.eligibleBundledCategoryPluginModels
import com.intellij.ide.plugins.unified.eligibleInstalledPluginModels
import com.intellij.ide.plugins.unified.initialPluginsQueryState
import com.intellij.ide.plugins.unified.loadUnifiedPluginInternalGroup
import com.intellij.ide.plugins.unified.pluginDetailsMode
import com.intellij.ide.plugins.unified.unifiedPluginSearchStatistics
import com.intellij.ide.plugins.unified.withEnrichmentReadiness
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.actionSystem.UiDataProvider
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.options.newEditor.SpotlightPainter
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.FUSEventSource
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.wm.WelcomeScreen
import com.intellij.openapi.wm.impl.welcomeScreen.PluginsTabFactory
import com.intellij.openapi.wm.impl.welcomeScreen.WelcomeScreenEventCollector
import com.intellij.platform.util.coroutines.childScope
import com.intellij.ui.ComponentUtil
import com.intellij.ui.components.labels.LinkListener
import com.intellij.util.application
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.ui.JBDimension
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.TextTransferable
import com.intellij.util.ui.launchOnShow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.awt.BorderLayout
import java.awt.Container
import java.awt.Dimension
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.util.concurrent.CancellationException
import java.util.function.Consumer
import javax.swing.JComponent
import javax.swing.JPanel
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

// @spec platform/platform-impl/spec/plugin-manager/unified-plugin-manager-ui.spec.md
// @spec platform/platform-impl/spec/plugin-manager/plugin-operations.spec.md
/**
 * Owns one unified Plugins page and coordinates its sources, state, view, and plugin session.
 *
 * The page scope owns source requests and presentation work. It ends during [dispose].
 * The application scope owns started install and update work, so that work can finish after the page closes.
 * The plugin session owns prepared settings changes. Disposal closes that session when no apply or reset operation owns it.
 */
internal class UnifiedPluginsPageSession @RequiresEdt(generateAssertion = false /* IJPL-115548 */) constructor(
  initialNavigation: PluginsPageInitialNavigation?,
  openSource: PluginManagerOpenSourceEnum,
  private val isStandaloneConfigurable: Boolean = false,
  localDataProviderFactory: (LegacyPluginUiHost) -> UnifiedPluginLocalDataProvider = ::DefaultUnifiedPluginLocalDataProvider,
  repositoryDataProviderFactory: () -> UnifiedPluginRepositoryDataProvider = ::DefaultUnifiedPluginRepositoryDataProvider,
  marketplaceDataProviderFactory:
    (LegacyPluginUiHost, UnifiedPluginRepositoryCache) -> UnifiedPluginMarketplaceDataProvider = { host, cache ->
    DefaultUnifiedPluginMarketplaceDataProvider(host, getActiveProject(), cache)
  },
  pluginUpdates: Flow<PluginUpdatesEvent> = PluginUpdatesService.getInstance().updatesFlow(),
  private val customizer: PluginManagerCustomizer? = PluginManagerCustomizer.getInstance(),
  pluginStatesReadiness: suspend () -> Unit = { customizer?.awaitPluginStatesLoaded() },
  sessionInitializationReadiness: suspend (LegacyPluginUiHost) -> Unit = { it.awaitSessionInitialization() },
  internalGroupLoader: suspend () -> UnifiedPluginInternalGroup? = ::loadUnifiedPluginInternalGroup,
  internalLoadingDelay: Duration = 100.milliseconds,
  updateAllExecutorFactory: ((CoroutineScope, JComponent) -> UnifiedPluginUpdateAllExecutor)? = null,
  private val unifiedSearchLogger: (UnifiedPluginSearchStatistics, Int) -> Unit = { statistics, searchIndex ->
    PluginManagerUsageCollector.performUnifiedSearch(getActiveProject(), statistics, searchIndex)
  },
) : PluginsPageSession {
  private val pageReadyStart = TimeSource.Monotonic.markNow()
  private val uiTracker = PluginManagerUiTracker()
  private val categoryPromotionProviders = activeCategoryPromotionProviders()
  private val categoryPromotionProvidersByCategory =
    categoryPromotionProviders.associateBy(PluginCategoryPromotionProvider::getCategoryName)
  private val densityVariant = UnifiedPluginsPageFeature.densityVariant()
  private val applicationScope = application.getService(PluginManagerCoroutineScopeHolder::class.java).coroutineScope
  private val pageScope: CoroutineScope = applicationScope.childScope(javaClass.name, Dispatchers.IO, true)
  private val host = LegacyPluginUiHost(
    parentScope = pageScope,
    operationScope = applicationScope,
    unifiedDetailsPageLayout = true,
    pluginIconScale = densityVariant.pluginIconScale,
    compactRows = densityVariant.compactRows,
  )
  private var updateAllOperationEventSink: (PluginModelEvent) -> Unit = {}
  private val listModel = ListPluginModel()
  private val repositoryCache = UnifiedPluginRepositoryCache(pageScope, repositoryDataProviderFactory())
  private val enrichmentReadiness: suspend () -> Unit = {
    pluginStatesReadiness()
    sessionInitializationReadiness(host)
  }
  private val localSource = UnifiedPluginLocalSourceCoordinator(
    scope = pageScope,
    dataProvider = localDataProviderFactory(host).withEnrichmentReadiness(enrichmentReadiness),
    updates = pluginUpdates,
    hostEvents = host.events,
    sessionId = host.sessionId,
    loadErrorMessage = IdeBundle.message("plugins.configurable.local.plugins.not.loaded"),
    hostEventObserver = { event -> updateAllOperationEventSink(event) },
  )
  private val initialQueryState = initialNavigation.toQueryState()
  private val marketplaceDataProvider = marketplaceDataProviderFactory(host, repositoryCache)
    .withEnrichmentReadiness(enrichmentReadiness)
  private val internalSource = UnifiedPluginInternalSourceCoordinator(
    scope = pageScope,
    loadGroup = internalGroupLoader,
    dataEnricher = marketplaceDataProvider,
    updates = pluginUpdates,
    loadingTitle = IdeBundle.message("plugins.configurable.internal"),
    loadErrorMessage = IdeBundle.message("plugins.configurable.internal.plugins.not.loaded"),
    loadingDelay = internalLoadingDelay,
  )
  private val marketplaceSource = UnifiedPluginMarketplaceSourceCoordinator(
    scope = pageScope,
    initialQuery = initialQueryState,
    dataProvider = marketplaceDataProvider,
    updates = pluginUpdates,
    loadErrorMessage = IdeBundle.message("plugins.configurable.search.result.not.loaded"),
  )
  private val repositorySource = UnifiedPluginRepositorySourceCoordinator(
    scope = pageScope,
    repositoryCache = repositoryCache,
    dataEnricher = marketplaceDataProvider,
    updates = pluginUpdates,
    loadErrorMessage = IdeBundle.message("plugins.configurable.repository.plugins.not.loaded"),
  )
  private val pageSource = UnifiedPluginsPageSourceCoordinator(
    pageScope,
    initialQueryState,
    localSource,
    internalSource,
    marketplaceSource,
    repositorySource,
    onSearchReset = PluginManagerUsageCollector::searchReset,
  )
  private val controller = UnifiedPluginsPageController(
    initialSections = pageSource.state.value.sections,
    initialQuery = pageSource.state.value.query,
    collapsedItemLimit = densityVariant.collapsedItemLimit,
    priorityBundledCategories = categoryPromotionProviders.asSequence()
      .filter(PluginCategoryPromotionProvider::isPriorityCategory)
      .mapTo(HashSet(), PluginCategoryPromotionProvider::getCategoryName),
  )
  private val updateAllButton = UnifiedPluginUpdateAllButton(::handleUpdateAllIntent)
  private val view: UnifiedPluginsPageView
  private val contentComponent: JComponent
  private val actions: UnifiedPluginsPageActions
  private val headerComponent: JComponent
  private val updateAllController: UnifiedPluginUpdateAllController
  private val pageSourceCollectionJob: Job
  private val updateAllUpdatesCollectionJob: Job
  private val updateAllStateCollectionJob: Job
  private val applyState = UnifiedPluginsPageApplyState()

  private val callbackLock = Any()
  private var pendingSelectedPluginIds: List<PluginId> = emptyList()
  private var pendingSelectionSourcesSettled = false
  private var shutdownCallbackExecuted = false
  private var applyScheduled = false
  private var disposeStarted = false
  private var disposed = false
  private var pendingUnifiedSearch: PendingUnifiedSearch? = null
  private var renderedRepositoryContentRevision = -1L
  private var pageReadyReported = false
  private var reportedSourceFailures: Set<UnifiedPluginSourceFailure> = emptySet()
  private var spotlightSearchActive = false
  private var queryIntentRevision = 0L
  private var settingsRequestRevision = 0L

  init {
    val searchListener = LinkListener<Any> { _, data ->
      val query = when (data) {
        is String -> data
        is TagComponent -> SearchQueryParser.getTagQuery(data.text)
        else -> return@LinkListener
      }
      applyQueryIntent(query, requestFocus = true)
    }
    val rowFactory = LegacyPluginRowFactory(host, listModel, searchListener, ::selectOccurrences)
    val detailsPresenter = LegacyPluginDetailsPresenter(host, searchListener)
    view = UnifiedPluginsPageView(
      onSearchChanged = { query -> applyQueryIntent(query, requestFocus = false) },
      onSelectionChanged = ::selectOccurrences,
      onSectionExpansionChanged = { sectionId, expanded ->
        controller.setSectionExpanded(sectionId, expanded)
        renderControllerState()
      },
      onSectionRetryRequested = pageSource::retry,
      onSearchControl = ::applySearchControl,
      onBundledCategoryAction = { category ->
        val models = eligibleBundledCategoryPluginModels(controller.state.value.sections, category)
        host.changeAllPluginsState(category.action == BundledPluginCategoryAction.EnableAll, models)
      },
      createBundledCategoryPromotion = { category ->
        categoryPromotionProvidersByCategory[category]?.createPromotionPanel()
      },
      rowFactory = rowFactory,
      detailsPresenter = detailsPresenter,
    )
    view.component.minimumSize = JBDimension(580, 380)
    view.component.preferredSize = JBDimension(800, 600)
    uiTracker.measure(PluginManagerUiMetric.UNIFIED_PAGE_RENDER) {
      view.render(controller.state.value)
    }
    contentComponent = createContentComponent()

    val updateAllExecutor = updateAllExecutorFactory?.invoke(applicationScope, contentComponent)
                            ?: PageSessionPluginUpdateAllExecutor(
                              operationContextFactory = host::createPluginUpdateOperationContext,
                              installedPluginProvider = { pluginId ->
                                localSource.state.value.listModelData.installedModels[pluginId]
                              },
                              updateStarter = { installedPlugin, update, operationContext ->
                                host.startPluginUpdate(installedPlugin, update, contentComponent, operationContext)
                              },
                              runOnEdt = { action ->
                                if (application.isDispatchThread) action()
                                else application.invokeLater(action, ModalityState.any())
                              },
                            )
    updateAllController = UnifiedPluginUpdateAllController(
      executor = updateAllExecutor,
      targetSink = localSource::setUpdateAllTargets,
    )
    updateAllOperationEventSink = updateAllController::acceptOperationEvent

    actions = UnifiedPluginsPageActions(
      parentComponent = contentComponent,
      pageScope = pageScope,
      host = host,
      customizer = customizer,
      onRefreshRequested = ::refresh,
      installedPlugins = { eligibleInstalledPluginModels(localSource.state.value.sections) },
      onPluginInstalledFromDisk = { callbackData ->
        onPluginInstalledFromDisk(callbackData, PluginSource.LOCAL)
      },
    )
    headerComponent = createHeaderComponent()
    customizer?.initCustomizer(contentComponent)
    renderPageSourceState(pageSource.state.value)
    updateAllButton.render(updateAllController.state.value.presentation)
    pageSourceCollectionJob = contentComponent.launchOnShow("UnifiedPluginsPageSession.pageSource") {
      pageSource.state.collect { state ->
        if (!disposed) renderPageSourceState(state)
      }
    }
    updateAllStateCollectionJob = contentComponent.launchOnShow("UnifiedPluginsPageSession.updateAllState") {
      updateAllController.state.collect { state ->
        if (!disposed) updateAllButton.render(state.presentation)
      }
    }
    updateAllUpdatesCollectionJob = pageScope.launch(start = CoroutineStart.UNDISPATCHED) {
      pluginUpdates.combine(localSource.state) { updates, localState ->
        val installedPluginIds = localState.listModelData.installedModels.keys
        updates.copy(enabledUpdates = updates.enabledUpdates.filter { it.pluginId in installedPluginIds })
      }.collect(updateAllController::acceptUpdates)
    }
    PluginManagerUsageCollector.logUnifiedSessionStarted(openSource)
    startUnifiedSearch(pageSource.state.value.query)
    pageSource.start()
  }

  @RequiresEdt(generateAssertion = false /* IJPL-115548 */)
  override fun getCenterComponent(controller: Configurable.TopComponentController): JComponent {
    controller.showProgressIndicator(false)
    controller.showResetAction(false)
    controller.setCenterComponentGap(JBUI.scale(if (isStandaloneConfigurable) 13 else -2))
    host.setTopController(controller)
    return headerComponent
  }

  override fun getComponent(): JComponent = contentComponent

  override fun getPreferredFocusedComponent(): JComponent = view.preferredFocusedComponent

  override fun isMarketplaceTabShowing(): Boolean = true

  override fun isInstalledTabShowing(): Boolean = true

  override fun setInstallSource(source: FUSEventSource?) {
    host.setInstallSource(source)
  }

  override fun cancel() {
    if (disposed || !applyState.resetWithSessionRemovalStarted()) return
    val resetJob = try {
      host.cancel(contentComponent)
    }
    catch (t: Throwable) {
      finishCancelResetFailure()
      throw t
    }
    resetJob.invokeOnCompletion { failure ->
      if (failure != null) finishCancelResetFailure()
    }
  }

  override fun isModified(): Boolean {
    return !disposed && (actions.isModified() || host.isModified())
  }

  override fun scheduleApply() {
    synchronized(callbackLock) {
      if (applyScheduled || disposed) return
      applyScheduled = true
    }
    application.invokeLater(
      {
        try {
          if (!disposed && isModified()) {
            apply()
            WelcomeScreenEventCollector.logPluginsModified()
          }
        }
        catch (exception: ConfigurationException) {
          Logger.getInstance(PluginsTabFactory::class.java).error(exception)
        }
        finally {
          synchronized(callbackLock) {
            applyScheduled = false
          }
        }
      },
      ModalityState.nonModal(),
    )
  }

  @Throws(ConfigurationException::class)
  override fun apply() {
    check(!disposed) { "Unified plugins page is disposed" }
    actions.apply()
    applyState.operationStarted()
    try {
      host.apply(
        contentComponent,
        Consumer { installedWithoutRestart ->
          val restartRequired = !installedWithoutRestart
          try {
            if (restartRequired) {
              installShutdownCallback()
            }
          }
          finally {
            finishApplyCallback(restartRequired)
          }
        },
        Consumer { error ->
          try {
            if (error !is CancellationException) {
              Logger.getInstance(PluginsTabFactory::class.java).error(error)
            }
          }
          finally {
            finishApplyCallback(restartRequired = false)
          }
        },
      )
    }
    catch (t: Throwable) {
      finishApplyCallback(restartRequired = false)
      throw t
    }
  }

  override fun reset() {
    if (disposed) return
    actions.reset()
    host.reset(contentComponent)
  }

  override fun selectAndEnable(descriptors: Set<IdeaPluginDescriptor>) {
    host.enable(descriptors)
    select(descriptors.map(IdeaPluginDescriptor::getPluginId))
  }

  override fun select(pluginIds: Collection<PluginId>) {
    if (disposed) return
    pendingSelectedPluginIds = pluginIds.toList()
    if (applyPendingSelection()) {
      view.render(controller.state.value)
    }
  }

  @RequiresEdt(generateAssertion = false /* IJPL-115548 */)
  override fun enableSearch(option: String?): Runnable? = enableSearch(option, ignoreTagMarketplaceTab = false)

  @RequiresEdt(generateAssertion = false /* IJPL-115548 */)
  override fun enableSearch(option: String?, ignoreTagMarketplaceTab: Boolean): Runnable? {
    val query = option.orEmpty()
    val calledFromSpotlight = isCalledFromSpotlightPainter()
    // SpotlightPainter calls enableSearch("") when Settings opens. Ignore this refresh before it invalidates a pending navigation request.
    // After Spotlight applies a user query, the same call clears that query normally.
    // Spotlight query changes keep focus in the Settings search field.
    if (query.isEmpty() && calledFromSpotlight && !spotlightSearchActive) return null

    val requestRevision = ++settingsRequestRevision
    val intentRevision = queryIntentRevision
    if (query.isEmpty() && pageSource.state.value.query.rawQuery.isEmpty()) {
      spotlightSearchActive = false
      return null
    }
    val scope = if (ignoreTagMarketplaceTab) PluginsQueryScope.Installed else PluginsQueryScope.Unified
    return Runnable {
      if (disposed || requestRevision != settingsRequestRevision || intentRevision != queryIntentRevision) return@Runnable
      applyQueryIntent(query, requestFocus = !calledFromSpotlight, scope = scope)
      if (calledFromSpotlight || query.isEmpty()) spotlightSearchActive = query.isNotEmpty()
    }
  }

  @RequiresEdt(generateAssertion = false /* IJPL-115548 */)
  override fun openMarketplaceTab(option: String) {
    applyQueryIntent(
      normalizeMarketplaceNavigationQuery(option),
      requestFocus = true,
      scope = PluginsQueryScope.Marketplace,
    )
  }

  @RequiresEdt(generateAssertion = false /* IJPL-115548 */)
  override fun openInstalledTab(option: String) {
    applyQueryIntent(
      option,
      requestFocus = true,
      scope = PluginsQueryScope.Installed,
    )
  }

  @RequiresEdt(generateAssertion = false /* IJPL-115548 */)
  override fun openInstalledTabWithSearch(option: String): Runnable? {
    openInstalledTab(option)
    return null
  }

  @RequiresEdt(generateAssertion = false /* IJPL-115548 */)
  override fun dispose() {
    synchronized(callbackLock) {
      if (disposeStarted) return
      disposeStarted = true
    }

    if (ComponentUtil.getParentOfType(WelcomeScreen::class.java, contentComponent) != null && isModified()) {
      apply()
      WelcomeScreenEventCollector.logPluginsModified()
    }

    val closeSessionNow = applyState.dispose()
    val installedPluginsState = InstalledPluginsState.getInstance()
    disposed = true
    try {
      pageSource.close()
      pageSourceCollectionJob.cancel()
      updateAllUpdatesCollectionJob.cancel()
      updateAllStateCollectionJob.cancel()
      updateAllController.close()
      updateAllOperationEventSink = {}
      val installationMovedToBackground = host.dispose(closeSession = closeSessionNow)
      if (installationMovedToBackground) {
        installedPluginsState.clearShutdownCallback()
      }
      view.close()
      PluginPriceService.cancel()
      installedPluginsState.runShutdownCallback()
      installedPluginsState.resetChangesAppliedWithoutRestart()
    }
    finally {
      pageScope.cancel()
    }
  }

  private fun createHeaderComponent(): JComponent {
    val installCallback = Consumer<PluginInstallCallbackData> { callbackData ->
      onPluginInstalledFromDisk(callbackData, PluginSource.REMOTE)
    }
    return object : JPanel(AdaptivePluginsHeaderLayout()), UiDataProvider {
      init {
        isOpaque = false
        add(view.searchComponent, GridBagConstraints().apply {
          gridx = 0
          gridy = 0
          fill = GridBagConstraints.HORIZONTAL
          anchor = GridBagConstraints.CENTER
          insets = JBUI.insetsRight(4)
        })
        add(
          updateAllButton.component,
          GridBagConstraints().apply {
            gridx = 1
            gridy = 0
            fill = GridBagConstraints.NONE
            anchor = GridBagConstraints.CENTER
            insets = JBUI.insetsRight(4)
          },
        )
        add(
          TabbedPaneHeaderComponent.createToolbar(
            actions.group,
            IdeBundle.message("plugin.manager.tooltip"),
            AllIcons.General.GearPlain,
          ),
          GridBagConstraints().apply {
            gridx = 2
            gridy = 0
            weightx = 1.0
            anchor = GridBagConstraints.WEST
          },
        )
      }

      override fun uiDataSnapshot(sink: DataSink) {
        sink[PluginManagerConfigurable.PLUGIN_INSTALL_CALLBACK_DATA_KEY] = installCallback
      }
    }
  }

  private fun createContentComponent(): JComponent {
    val copyProvider = object : CopyProvider {
      override fun performCopy(dataContext: DataContext) {
        val item = selectedItem() ?: return
        val model = item.modelHandle?.model ?: return
        val text = String.format("%s (%s)", model.name, model.version)
        CopyPasteManager.getInstance().setContents(TextTransferable(text as String?))
      }

      override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

      override fun isCopyEnabled(dataContext: DataContext): Boolean = selectedItem()?.modelHandle != null

      override fun isCopyVisible(dataContext: DataContext): Boolean = true
    }
    return object : JPanel(BorderLayout()), UiDataProvider {
      init {
        add(view.component, BorderLayout.CENTER)
      }

      override fun uiDataSnapshot(sink: DataSink) {
        sink[PlatformDataKeys.COPY_PROVIDER] = copyProvider
      }
    }
  }

  private fun onPluginInstalledFromDisk(callbackData: PluginInstallCallbackData, source: PluginSource) {
    PluginModelAsyncOperationsExecutor.updateErrors(
      pageScope,
      host.sessionId,
      callbackData.pluginDescriptor.pluginId,
    ) { errors ->
      if (!disposed) {
        host.completeInstallFromDisk(callbackData, errors, source)
      }
    }
  }

  private fun installShutdownCallback() {
    val installedPluginsState = InstalledPluginsState.getInstance()
    synchronized(callbackLock) {
      if (shutdownCallbackExecuted || !host.createShutdownCallback) return
      installedPluginsState.setShutdownCallback {
        synchronized(callbackLock) {
          if (shutdownCallbackExecuted) return@setShutdownCallback
          shutdownCallbackExecuted = true
        }
        application.invokeLater {
          if (application.isExitInProgress) return@invokeLater
          if (customizer != null) {
            host.requestRestart(customizer, headerComponent)
          }
          else {
            host.closeSession()
            PluginManagerConfigurable.shutdownOrRestartApp()
          }
        }
      }
    }
    synchronized(callbackLock) {
      if (disposeStarted && !shutdownCallbackExecuted) {
        installedPluginsState.runShutdownCallback()
      }
    }
  }

  private fun finishApplyCallback(restartRequired: Boolean) {
    if (applyState.operationFinished(restartRequired)) {
      host.closeSession()
    }
  }

  private fun finishCancelResetFailure() {
    if (applyState.resetWithSessionRemovalFailed()) {
      host.closeSession()
    }
  }

  private fun applyQueryIntent(
    query: String,
    requestFocus: Boolean,
    scope: PluginsQueryScope = PluginsQueryScope.Unified,
  ) {
    if (disposed) return
    queryIntentRevision++
    view.setSearchQuery(query)
    val previousRevision = pageSource.state.value.query.revision
    pageSource.setQuery(query, scope)
    val updatedQuery = pageSource.state.value.query
    if (updatedQuery.revision != previousRevision) startUnifiedSearch(updatedQuery)
    if (requestFocus) view.requestSearchFocus()
  }

  private fun applySearchControl(intent: UnifiedPluginSearchControlIntent) {
    if (disposed) return
    queryIntentRevision++
    pageSource.applySearchControl(intent)
  }

  private fun selectOccurrences(occurrenceIds: List<PluginOccurrenceId>) {
    if (disposed) return
    pendingSelectedPluginIds = emptyList()
    controller.selectOccurrences(occurrenceIds)
    renderControllerState()
  }

  private fun refresh() {
    pageSource.refresh()
  }

  @RequiresEdt(generateAssertion = false /* IJPL-115548 */)
  private fun handleUpdateAllIntent(intent: UnifiedPluginUpdateAllIntent) {
    when (intent) {
      UnifiedPluginUpdateAllIntent.Update -> updateAllController.requestUpdateAll()
      UnifiedPluginUpdateAllIntent.Restart -> {
        if (customizer != null) {
          host.requestRestart(customizer, headerComponent)
        }
        else {
          host.closeSession()
          PluginManagerConfigurable.shutdownOrRestartApp()
        }
      }
    }
  }

  @RequiresEdt(generateAssertion = false /* IJPL-115548 */)
  private fun renderPageSourceState(state: UnifiedPluginsPageSourceState) {
    pendingSelectionSourcesSettled = state.sourcesSettled
    if (renderedRepositoryContentRevision != state.repositoryContentRevision) {
      renderedRepositoryContentRevision = state.repositoryContentRevision
      host.updateCustomRepositoryPlugins(state.settledRepositoryPlugins)
    }
    val data = state.listModelData
    listModel.replaceAll(data.installedModels, data.errors, data.installationStates, data.updateSources)
    controller.replaceSourceState(state.query, state.sections, state.mayEstablishSelection, state.searchControls)
    renderControllerState()
    reportSourceFailures(state)
    reportPageReady(state)
    reportUnifiedSearchIfSettled(state)
  }

  private fun startUnifiedSearch(query: PluginsQueryState) {
    pendingUnifiedSearch = if (query.normalizedQuery.isEmpty()) {
      null
    }
    else {
      PendingUnifiedSearch(query.revision, PluginManagerUsageCollector.updateAndGetSearchIndex(), TimeSource.Monotonic.markNow())
    }
  }

  private fun reportUnifiedSearchIfSettled(state: UnifiedPluginsPageSourceState) {
    val pendingSearch = pendingUnifiedSearch ?: return
    if (pendingSearch.revision != state.query.revision || !state.sourcesSettled) {
      return
    }
    pendingUnifiedSearch = null
    val statistics = unifiedPluginSearchStatistics(state)
    uiTracker.measure(PluginManagerUiMetric.UNIFIED_SEARCH_LATENCY, pendingSearch.start)
    if (statistics.resultCounts.values.none { it > 0 }) {
      uiTracker.logEvent(PluginManagerUiEvent.UNIFIED_SEARCH_EMPTY)
    }
    val failedSources = state.sourceFailures.toSearchSourceKinds(statistics.sourceKinds)
    if (failedSources.isNotEmpty() && statistics.sourceKinds.any { it !in failedSources }) {
      uiTracker.logEvent(PluginManagerUiEvent.UNIFIED_SEARCH_PARTIAL)
    }
    unifiedSearchLogger(statistics, pendingSearch.searchIndex)
  }

  private fun reportPageReady(state: UnifiedPluginsPageSourceState) {
    if (pageReadyReported || !state.sourcesSettled) return
    pageReadyReported = true
    uiTracker.measure(PluginManagerUiMetric.UNIFIED_PAGE_READY, pageReadyStart)
  }

  private fun reportSourceFailures(state: UnifiedPluginsPageSourceState) {
    val currentFailures = state.sourceFailures
    (currentFailures - reportedSourceFailures).forEach { failure ->
      uiTracker.logEvent(failure.event)
    }
    reportedSourceFailures = currentFailures
  }

  private fun renderControllerState() {
    applyPendingSelection()
    uiTracker.measure(PluginManagerUiMetric.UNIFIED_PAGE_RENDER) {
      view.render(controller.state.value)
    }
  }

  private fun applyPendingSelection(): Boolean {
    if (pendingSelectedPluginIds.isEmpty()) return false
    val state = controller.state.value
    val pendingIds = pendingSelectedPluginIds.toSet()
    val occurrences = state.sections.flatMap { section ->
      section.items.filter { it.pluginId in pendingIds }.map { section.occurrenceId(it.pluginId) }
    }.distinctBy(PluginOccurrenceId::pluginId)
    if (occurrences.isEmpty()) {
      if (pendingSelectionSourcesSettled) pendingSelectedPluginIds = emptyList()
      return false
    }
    val preferredMode = occurrences.groupBy { pluginDetailsMode(it.sectionId) }
      .maxByOrNull { it.value.size }
      ?.key
    val compatibleOccurrences = occurrences.filter { pluginDetailsMode(it.sectionId) == preferredMode }
    if (occurrences.mapTo(HashSet(), PluginOccurrenceId::pluginId).containsAll(pendingIds) || pendingSelectionSourcesSettled) {
      pendingSelectedPluginIds = emptyList()
    }
    return controller.selectAndRevealOccurrences(compatibleOccurrences)
  }

  private fun selectedItem() = controller.state.value.selectedOccurrence?.let { selectedOccurrence ->
    controller.state.value.sections
      .firstOrNull { it.id == selectedOccurrence.sectionId }
      ?.items
      ?.firstOrNull { it.pluginId == selectedOccurrence.pluginId }
  }
}

private class AdaptivePluginsHeaderLayout : GridBagLayout() {
  override fun preferredLayoutSize(parent: Container): Dimension = calculateSize(parent, includeSearchWidth = true)

  override fun minimumLayoutSize(parent: Container): Dimension = calculateSize(parent, includeSearchWidth = false)

  override fun layoutContainer(parent: Container) {
    val visibleComponents = parent.components.filter { it.isVisible }
    if (visibleComponents.isEmpty()) return

    val parentInsets = parent.insets
    val availableWidth = (parent.width - parentInsets.left - parentInsets.right).coerceAtLeast(0)
    val searchComponent = visibleComponents.first()
    val fixedWidth = visibleComponents.sumOf { component ->
      val componentInsets = getConstraints(component).insets
      componentInsets.left + componentInsets.right +
      if (component === searchComponent) 0 else component.preferredSize.width
    }
    val searchWidth = (availableWidth - fixedWidth).coerceIn(0, searchComponent.preferredSize.width)

    var x = parentInsets.left
    for (component in visibleComponents) {
      val constraints = getConstraints(component)
      val componentInsets = constraints.insets
      val preferredSize = component.preferredSize
      val width = if (component === searchComponent) searchWidth else preferredSize.width
      val availableHeight = (parent.height - parentInsets.top - parentInsets.bottom -
                             componentInsets.top - componentInsets.bottom).coerceAtLeast(0)
      val height = preferredSize.height.coerceAtMost(availableHeight)
      val y = parentInsets.top + componentInsets.top + (availableHeight - height) / 2
      x += componentInsets.left
      component.setBounds(x, y, width, height)
      x += width + componentInsets.right
    }
  }

  private fun calculateSize(parent: Container, includeSearchWidth: Boolean): Dimension {
    val visibleComponents = parent.components.filter { it.isVisible }
    val parentInsets = parent.insets
    var width = parentInsets.left + parentInsets.right
    var height = 0
    for ((index, component) in visibleComponents.withIndex()) {
      val componentInsets = getConstraints(component).insets
      val preferredSize = component.preferredSize
      if (includeSearchWidth || index > 0) {
        width += preferredSize.width
      }
      width += componentInsets.left + componentInsets.right
      height = maxOf(height, preferredSize.height + componentInsets.top + componentInsets.bottom)
    }
    return Dimension(width, height + parentInsets.top + parentInsets.bottom)
  }
}

private data class PendingUnifiedSearch(
  val revision: Long,
  val searchIndex: Int,
  val start: TimeMark,
)

private fun PluginsPageInitialNavigation?.toQueryState(): PluginsQueryState {
  if (this == null) return initialPluginsQueryState("")
  val scope = when (target) {
    PluginsPageInitialNavigationTarget.Marketplace -> PluginsQueryScope.Marketplace
    PluginsPageInitialNavigationTarget.Installed -> PluginsQueryScope.Installed
  }
  val normalizedQuery = when (target) {
    PluginsPageInitialNavigationTarget.Marketplace -> normalizeMarketplaceNavigationQuery(query)
    PluginsPageInitialNavigationTarget.Installed -> query
  }
  return initialPluginsQueryState(normalizedQuery, scope)
}

private fun normalizeMarketplaceNavigationQuery(query: String): String {
  return query.takeUnless { it.trim() == "/suggested" }.orEmpty()
}

private fun isCalledFromSpotlightPainter(): Boolean {
  return SEARCH_CALLER_WALKER.walk { frames ->
    frames.anyMatch { SpotlightPainter::class.java.isAssignableFrom(it.declaringClass) }
  }
}

private val SEARCH_CALLER_WALKER = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE)

private val UnifiedPluginsPageSourceState.sourcesSettled: Boolean
  get() = internalDescriptorSettled && repositoryPlugins != null && sections.none { it.status is PluginSectionStatus.Loading }

private val UnifiedPluginsPageSourceState.sourceFailures: Set<UnifiedPluginSourceFailure>
  get() = sections.asSequence()
    .filter { section ->
      section.status is PluginSectionStatus.Degraded || section.status is PluginSectionStatus.Failed
    }
    .mapNotNull { it.id.sourceFailure }
    .toSet()

private val PluginSectionId.sourceFailure: UnifiedPluginSourceFailure?
  get() = when (this) {
    PluginSectionId.Installing, PluginSectionId.Installed, PluginSectionId.Bundled -> UnifiedPluginSourceFailure.Local
    PluginSectionId.Internal -> UnifiedPluginSourceFailure.Internal
    PluginSectionId.Suggested, PluginSectionId.Marketplace -> UnifiedPluginSourceFailure.Marketplace
    PluginSectionId.CustomRepositoryCatalog, is PluginSectionId.CustomRepository -> UnifiedPluginSourceFailure.Repository
  }

private enum class UnifiedPluginSourceFailure(val event: PluginManagerUiEvent) {
  Local(PluginManagerUiEvent.UNIFIED_LOCAL_LOAD_ERROR),
  Internal(PluginManagerUiEvent.UNIFIED_INTERNAL_LOAD_ERROR),
  Marketplace(PluginManagerUiEvent.UNIFIED_MARKETPLACE_LOAD_ERROR),
  Repository(PluginManagerUiEvent.UNIFIED_REPOSITORY_LOAD_ERROR),
}

private fun Set<UnifiedPluginSourceFailure>.toSearchSourceKinds(
  eligibleSources: Set<UnifiedPluginSearchSourceKind>,
): Set<UnifiedPluginSearchSourceKind> = buildSet {
  if (UnifiedPluginSourceFailure.Local in this@toSearchSourceKinds) add(UnifiedPluginSearchSourceKind.LOCAL)
  if (UnifiedPluginSourceFailure.Internal in this@toSearchSourceKinds) add(UnifiedPluginSearchSourceKind.INTERNAL)
  if (UnifiedPluginSourceFailure.Marketplace in this@toSearchSourceKinds) {
    add(UnifiedPluginSearchSourceKind.SUGGESTED)
    add(UnifiedPluginSearchSourceKind.MARKETPLACE)
  }
  if (UnifiedPluginSourceFailure.Repository in this@toSearchSourceKinds) add(UnifiedPluginSearchSourceKind.CUSTOM_REPOSITORY)
}.intersect(eligibleSources)

private fun activeCategoryPromotionProviders(): List<PluginCategoryPromotionProvider> {
  if (!Registry.`is`("ide.plugins.category.promotion.enabled")) return emptyList()
  return CATEGORY_PROMOTION_EP_NAME.extensionList
}

private val CATEGORY_PROMOTION_EP_NAME: ExtensionPointName<PluginCategoryPromotionProvider> =
  ExtensionPointName.create("com.intellij.pluginCategoryPromotionProvider")
