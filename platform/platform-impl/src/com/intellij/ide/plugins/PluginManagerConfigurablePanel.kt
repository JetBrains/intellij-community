@file:Suppress("removal", "DEPRECATION", "UsePropertyAccessSyntax", "ReplaceJavaStaticMethodWithKotlinAnalog", "CascadeIf")

// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.ide.CopyProvider
import com.intellij.ide.DataManager
import com.intellij.ide.IdeBundle
import com.intellij.ide.plugins.certificates.PluginCertificateManager
import com.intellij.ide.plugins.marketplace.statistics.PluginManagerUsageCollector
import com.intellij.ide.plugins.newui.ListPluginComponent
import com.intellij.ide.plugins.newui.MyPluginModel
import com.intellij.ide.plugins.newui.PluginManagerCustomizer
import com.intellij.ide.plugins.newui.PluginModelAsyncOperationsExecutor
import com.intellij.ide.plugins.newui.PluginModelFacade
import com.intellij.ide.plugins.newui.PluginPriceService
import com.intellij.ide.plugins.newui.PluginUiModel
import com.intellij.ide.plugins.newui.PluginUpdatesService
import com.intellij.ide.plugins.newui.PluginsGroup
import com.intellij.ide.plugins.newui.PluginsGroupComponent
import com.intellij.ide.plugins.newui.PluginsTab
import com.intellij.ide.plugins.newui.SearchWords
import com.intellij.ide.plugins.newui.TabbedPaneHeaderComponent
import com.intellij.ide.plugins.newui.UiPluginManager
import com.intellij.ide.util.PropertiesComponent
import com.intellij.idea.AppMode
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CheckedActionGroup
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.actionSystem.impl.PresentationFactory
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ModalityState.any
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.DumbAwareToggleAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupListener
import com.intellij.openapi.ui.popup.LightweightWindowEvent
import com.intellij.openapi.updateSettings.impl.PluginAutoUpdateListener
import com.intellij.openapi.updateSettings.impl.UpdateOptions
import com.intellij.openapi.updateSettings.impl.UpdateSettings
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.FUSEventSource
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.wm.WelcomeScreen
import com.intellij.openapi.wm.impl.welcomeScreen.PluginsTabFactory
import com.intellij.openapi.wm.impl.welcomeScreen.WelcomeScreenEventCollector
import com.intellij.platform.util.coroutines.childScope
import com.intellij.ui.ComponentUtil
import com.intellij.ui.GotItTooltip
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.labels.LinkLabel
import com.intellij.ui.components.labels.LinkListener
import com.intellij.ui.popup.ActionPopupOptions
import com.intellij.ui.popup.PopupFactoryImpl
import com.intellij.util.application
import com.intellij.util.asDisposable
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.messages.MessageBusConnection
import com.intellij.util.net.HttpProxyConfigurable
import com.intellij.util.ui.JBDimension
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.TextTransferable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import java.awt.Component
import java.awt.Dimension
import java.awt.Point
import java.util.function.Consumer
import javax.accessibility.AccessibleContext
import javax.accessibility.AccessibleRole
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.ScrollPaneConstants

@ApiStatus.Internal
class PluginManagerConfigurablePanel @RequiresEdt constructor(searchQuery: String?) : Disposable {
  private val coroutineScope: CoroutineScope

  private val pluginModelFacade: PluginModelFacade
  private val pluginUpdatesService: PluginUpdatesService
  private val pluginManagerCustomizer: PluginManagerCustomizer? = PluginManagerCustomizer.getInstance()

  private val tabHeaderComponent: TabbedPaneHeaderComponent
  private val installedTabHeaderUpdatesCountIcon: CountIcon = CountIcon()

  private val marketplaceTab: MarketplacePluginsTab
  private val installedTab: InstalledPluginsTab
  private val cardPanel: MultiPanel

  private var laterSearchQuery: String? = null
  private var forceShowInstalledTabForTag: Boolean = false
  private var showMarketplaceTab: Boolean = false

  private var pluginsAutoUpdateEnabled: Boolean? = null

  @Volatile
  private var disposeStarted: Boolean = false

  private val callbackLock: Any = Any()
  private var shutdownCallbackExecuted: Boolean = false
  private var applyScheduled: Boolean = false

  init {
    pluginModelFacade = PluginModelFacade(MyPluginModel(null))
    val parentScope = application.getService(PluginManagerCoroutineScopeHolder::class.java).coroutineScope
    val childScope = parentScope.childScope(javaClass.name, Dispatchers.IO, true)
    pluginModelFacade.getModel().coroutineScope = childScope
    coroutineScope = childScope

    pluginUpdatesService =
      UiPluginManager.getInstance().subscribeToUpdatesCount(pluginModelFacade.getModel().sessionId) { updatesCount ->
        coroutineScope.launch(Dispatchers.EDT + any().asContextElement()) { onPluginUpdatesRecalculation(updatesCount) }
      }
    pluginModelFacade.getModel().pluginUpdatesService = pluginUpdatesService

    CustomPluginRepositoryService.getInstance().clearCache()

    marketplaceTab = createMarketplaceTab()
    installedTab = createInstalledTab()

    val selectionTab = getStoredSelectionTab()
    cardPanel = createCardPanel(selectionTab)
    tabHeaderComponent = createTabHeaderComponent(selectionTab)

    laterSearchQuery = searchQuery

    UiPluginManager.getInstance().updateDescriptorsForInstalledPlugins()

    PluginManagerUsageCollector.sessionStarted()

    if (laterSearchQuery != null) {
      val search = enableSearch(laterSearchQuery, forceShowInstalledTabForTag)
      if (search != null) {
        application.invokeLater(search, any())
      }
      laterSearchQuery = null
      forceShowInstalledTabForTag = false
    }
    if (pluginManagerCustomizer != null) {
      pluginManagerCustomizer.initCustomizer(cardPanel)
    }
  }

  @RequiresEdt
  private fun createCardPanel(selectionTab: Int): MultiPanel {
    val cardPanel = object : MultiPanel() {
      override fun create(key: Int?): JComponent {
        if (key == MARKETPLACE_TAB) {
          return marketplaceTab.createPanel()
        }
        if (key == INSTALLED_TAB) {
          return installedTab.createPanel()
        }
        return super.create(key)
      }
    }
    cardPanel.minimumSize = JBDimension(580, 380)
    cardPanel.preferredSize = JBDimension(800, 600)
    cardPanel.select(selectionTab, true)
    return cardPanel
  }

  private fun createTabHeaderComponent(selectionTab: Int): TabbedPaneHeaderComponent {
    val tabHeaderComponent = object : TabbedPaneHeaderComponent(createGearActions(), { index ->
      cardPanel.select(index, true)
      storeSelectionTab(index)

      val query = if (index == MARKETPLACE_TAB) installedTab.searchQuery else marketplaceTab.searchQuery
      if (index == MARKETPLACE_TAB) {
        marketplaceTab.searchQuery = query
      }
      else {
        installedTab.searchQuery = query
      }
    }) {
      override fun uiDataSnapshot(sink: DataSink) {
        sink.set(PluginManagerConfigurable.PLUGIN_INSTALL_CALLBACK_DATA_KEY, Consumer { callbackData -> onPluginInstalledFromDisk(callbackData) })
      }
    }
    tabHeaderComponent.createGearGotIt()
    tabHeaderComponent.addTab(IdeBundle.message("plugin.manager.tab.marketplace"), null)
    tabHeaderComponent.addTab(IdeBundle.message("plugin.manager.tab.installed"), installedTabHeaderUpdatesCountIcon)
    tabHeaderComponent.setListener()
    tabHeaderComponent.setSelection(selectionTab)
    return tabHeaderComponent
  }

  fun getCenterComponent(controller: Configurable.TopComponentController): JComponent {
    pluginModelFacade.getModel().setTopController(controller)
    return tabHeaderComponent
  }

  fun getTopComponent(): JComponent {
    return getCenterComponent(Configurable.TopComponentController.EMPTY)
  }

  fun getComponent(): JComponent {
    return cardPanel
  }

  fun isMarketplaceTabShowing(): Boolean {
    return tabHeaderComponent.getSelectionTab() == MARKETPLACE_TAB
  }

  fun isInstalledTabShowing(): Boolean {
    return tabHeaderComponent.getSelectionTab() == INSTALLED_TAB
  }

  private fun createGearActions(): DefaultActionGroup {
    val actions = DefaultActionGroup()
    if (PluginManagementPolicy.getInstance().isPluginAutoUpdateAllowed()) {
      val state = UpdateSettings.getInstance().getState()
      pluginsAutoUpdateEnabled = state.isPluginsAutoUpdateEnabled

      val connect: MessageBusConnection = application.getMessageBus()
        .connect(coroutineScope.asDisposable())
      connect.subscribe(PluginAutoUpdateListener.TOPIC, object : PluginAutoUpdateListener {
        override fun settingsChanged() {
          pluginsAutoUpdateEnabled = state.isPluginsAutoUpdateEnabled
        }
      })

      actions.add(UpdatePluginsAutomaticallyToggleAction())
      actions.addSeparator()
    }
    actions.add(ManagePluginRepositoriesAction())
    actions.add(OpenHttpProxyConfigurableAction())
    actions.addSeparator()
    actions.add(ManagePluginCertificatesAction())

    actions.add(CustomInstallPluginFromDiskAction())
    if (pluginManagerCustomizer != null) {
      actions.addAll(pluginManagerCustomizer.getExtraPluginsActions())
    }
    actions.addSeparator()
    actions.add(ChangePluginStateAction(false))
    actions.add(ChangePluginStateAction(true))

    if (application.isInternal) {
      actions.addSeparator()
      actions.add(ResetConfigurableAction())
    }
    return actions
  }

  private fun TabbedPaneHeaderComponent.createGearGotIt() {
    if (!PluginManagementPolicy.getInstance().isPluginAutoUpdateAllowed() ||
        UpdateSettings.getInstance().getState().isPluginsAutoUpdateEnabled ||
        AppMode.isRemoteDevHost()) {
      return
    }

    val title = IdeBundle.message("plugin.manager.plugins.auto.update.title")
    val tooltip = GotItTooltip(title, IdeBundle.message("plugin.manager.plugins.auto.update.description"), this@PluginManagerConfigurablePanel)
    tooltip.withHeader(title)
    tooltip.show(getComponent(1) as JComponent) { component, _ ->
      Point(component.getWidth() / 2, (component as JComponent).visibleRect.height)
    }
  }

  private fun resetPanels() {
    CustomPluginRepositoryService.getInstance().clearCache()
    marketplaceTab.resetCache()
    pluginUpdatesService.recalculateUpdates()
    marketplaceTab.onPanelReset(tabHeaderComponent.getSelectionTab() == MARKETPLACE_TAB)
  }

  private fun onPluginUpdatesRecalculation(updatesCount: Int?) {
    val count = updatesCount ?: 0
    val text = Integer.toString(count)

    val tooltip = PluginUpdatesService.getUpdatesTooltip()
    tabHeaderComponent.setTabTooltip(INSTALLED_TAB, tooltip)

    installedTab.onPluginUpdatesRecalculation(updatesCount, tooltip)

    installedTabHeaderUpdatesCountIcon.setText(text)
    tabHeaderComponent.update()
  }

  private fun createMarketplaceTab(): MarketplacePluginsTab {
    return MarketplacePluginsTab(pluginModelFacade, coroutineScope, pluginManagerCustomizer, pluginUpdatesService)
  }

  private fun createInstalledTab(): InstalledPluginsTab {
    val installedPluginsTab = InstalledPluginsTab(
      pluginModelFacade,
      pluginUpdatesService,
      coroutineScope,
      { _ -> tabHeaderComponent.setSelectionWithEvents(MARKETPLACE_TAB) },
    )

    pluginModelFacade.getModel().setCancelInstallCallback { descriptor ->
      val installedSearchPanel = installedTab.getInstalledSearchPanel()

      val group: PluginsGroup = installedSearchPanel.group

      if (group.ui != null && group.ui!!.findComponent(descriptor.pluginId) != null) {
        installedSearchPanel.panel.removeFromGroup(group, descriptor)
        group.titleWithCount()
        installedSearchPanel.fullRepaint()

        if (group.getModels().isEmpty()) {
          installedSearchPanel.removeGroup()
        }
      }
    }

    return installedPluginsTab
  }

  @Suppress("SameParameterValue")
  fun setInstallSource(source: FUSEventSource?) {
    pluginModelFacade.getModel().setInstallSource(source)
  }

  override fun dispose() {
    synchronized(callbackLock) {
      disposeStarted = true
    }

    if (ComponentUtil.getParentOfType(WelcomeScreen::class.java, cardPanel) != null && isModified()) {
      scheduleApply()
    }
    val pluginsState = InstalledPluginsState.getInstance()
    if (pluginModelFacade.getModel().toBackground()) {
      pluginsState.clearShutdownCallback()
    }

    marketplaceTab.dispose()
    installedTab.dispose()
    marketplaceTab.dispose() // FIXME why the second time???

    installedTab.getInstalledSearchPanel().dispose()

    pluginUpdatesService.dispose()
    PluginPriceService.cancel()

    pluginsState.runShutdownCallback()
    pluginsState.resetChangesAppliedWithoutRestart()

    Disposer.dispose(this)
    coroutineScope.cancel(null)
  }

  fun cancel() {
    pluginModelFacade.getModel().cancel(cardPanel, true)
  }

  fun isModified(): Boolean {
    if (pluginsAutoUpdateEnabled != null &&
        UpdateSettings.getInstance().getState().isPluginsAutoUpdateEnabled != pluginsAutoUpdateEnabled) {
      return true
    }
    return pluginModelFacade.getModel().isModified()
  }

  fun scheduleApply() {
    synchronized(callbackLock) {
      if (applyScheduled) {
        return
      }
      applyScheduled = true
    }
    application.invokeLater({
      try {
        if (isModified()) {
          apply()
          WelcomeScreenEventCollector.logPluginsModified()
          synchronized(callbackLock) {
            if (disposeStarted && !shutdownCallbackExecuted) {
              InstalledPluginsState.getInstance().runShutdownCallback()
            }
          }
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
    }, ModalityState.nonModal())
  }

  @Throws(ConfigurationException::class)
  fun apply() {
    if (pluginsAutoUpdateEnabled != null) {
      val state: UpdateOptions = UpdateSettings.getInstance().getState()
      if (state.isPluginsAutoUpdateEnabled != pluginsAutoUpdateEnabled) {
        UiPluginManager.getInstance().setPluginsAutoUpdateEnabled(pluginsAutoUpdateEnabled!!)
      }
    }

    pluginModelFacade.getModel().applyWithCallback(cardPanel) { installedWithoutRestart ->
      if (installedWithoutRestart) {
        return@applyWithCallback
      }
      val installedPluginsState = InstalledPluginsState.getInstance()

      synchronized(callbackLock) {
        if (shutdownCallbackExecuted) {
          return@applyWithCallback
        }

        if (pluginModelFacade.getModel().createShutdownCallback) {
          installedPluginsState.setShutdownCallback {
            synchronized(callbackLock) {
              if (shutdownCallbackExecuted) {
                return@setShutdownCallback
              }
              shutdownCallbackExecuted = true
            }

            application.invokeLater {
              if (application.isExitInProgress) return@invokeLater // already shutting down
              if (pluginManagerCustomizer != null) {
                pluginManagerCustomizer.requestRestart(pluginModelFacade, tabHeaderComponent)
                return@invokeLater
              }
              pluginModelFacade.closeSession()
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
  }

  fun reset() {
    if (pluginsAutoUpdateEnabled != null) {
      pluginsAutoUpdateEnabled = UpdateSettings.getInstance().getState().isPluginsAutoUpdateEnabled
    }
    pluginModelFacade.getModel().clear(cardPanel)
  }

  fun selectAndEnable(descriptors: Set<IdeaPluginDescriptor>) {
    pluginModelFacade.getModel().enable(descriptors)
    select(descriptors.map { it.pluginId })
  }

  fun select(pluginIds: Collection<PluginId>) {
    updateSelectionTab(INSTALLED_TAB)

    val components = ArrayList<ListPluginComponent>()

    for (pluginId in pluginIds) {
      val component = findInstalledPluginById(pluginId)
      if (component != null) {
        components.add(component)
      }
    }

    if (!components.isEmpty()) {
      installedTab.getInstalledPanel().setSelection(components)
    }
  }

  fun enableSearch(option: String?): Runnable? {
    return enableSearch(option, false)
  }

  fun enableSearch(option: String?, ignoreTagMarketplaceTab: Boolean): Runnable? {
    if (StringUtil.isEmpty(option) &&
        (tabHeaderComponent.getSelectionTab() == MARKETPLACE_TAB || installedTab.getInstalledSearchPanel().isQueryEmpty)) {
      return null
    }

    return Runnable {
      var marketplace = !ignoreTagMarketplaceTab && option != null && option.startsWith(SearchWords.TAG.value)
      if (showMarketplaceTab) {
        marketplace = true
        showMarketplaceTab = false
      }
      updateSelectionTab(if (marketplace) MARKETPLACE_TAB else INSTALLED_TAB)

      val tab: PluginsTab = if (marketplace) marketplaceTab else installedTab
      tab.clearSearchPanel(option ?: "")

      if (!StringUtil.isEmpty(option)) {
        tab.showSearchPanel(option!!)
      }
    }
  }

  fun openMarketplaceTab(option: String) {
    laterSearchQuery = option
    showMarketplaceTab = true
    updateSelectionTab(MARKETPLACE_TAB)
    marketplaceTab.clearSearchPanel(option)
    marketplaceTab.showSearchPanel(option)
  }

  fun openInstalledTab(option: String) {
    laterSearchQuery = option
    showMarketplaceTab = false
    forceShowInstalledTabForTag = true
    updateSelectionTab(INSTALLED_TAB)
  }

  @RequiresEdt
  private fun onPluginInstalledFromDisk(callbackData: PluginInstallCallbackData) {
    PluginModelAsyncOperationsExecutor.updateErrors(
      coroutineScope,
      pluginModelFacade.getModel().sessionId,
      callbackData.pluginDescriptor.pluginId,
    ) { errors ->
      updateAfterPluginInstalledFromDisk(callbackData, errors)
    }
  }

  private fun updateAfterPluginInstalledFromDisk(callbackData: PluginInstallCallbackData, errors: List<HtmlChunk>) {
    pluginModelFacade.getModel().pluginInstalledFromDisk(callbackData, errors)

    val select = false
    updateSelectionTab(INSTALLED_TAB)

    installedTab.clearSearchPanel("")

    val component = if (select) findInstalledPluginById(callbackData.pluginDescriptor.pluginId) else null
    if (component != null) {
      installedTab.getInstalledPanel().setSelection(component)
    }
  }

  private fun updateSelectionTab(tab: Int) {
    if (tabHeaderComponent.getSelectionTab() != tab) {
      tabHeaderComponent.setSelectionWithEvents(tab)
    }
  }

  private fun findInstalledPluginById(pluginId: PluginId): ListPluginComponent? {
    for (group in installedTab.getInstalledGroups()) {
      val component = group.findComponent(pluginId)
      if (component != null) {
        return component
      }
    }
    return null
  }

  open class LinkLabelButton<T> : LinkLabel<T> {
    constructor(@NlsContexts.LinkLabel text: String, icon: Icon?) : super(text, icon)

    @Suppress("UNCHECKED_CAST")
    constructor(@NlsContexts.LinkLabel text: String, icon: Icon?, aListener: LinkListener<*>?) : super(text, icon, aListener as LinkListener<T>?)

    @Suppress("UNCHECKED_CAST")
    constructor(
      @NlsContexts.LinkLabel text: String,
      icon: Icon?,
      aListener: LinkListener<*>?,
      aLinkData: T?,
    ) : super(text, icon, aListener as LinkListener<T>?, aLinkData)

    override fun getAccessibleContext(): AccessibleContext {
      if (accessibleContext == null) {
        accessibleContext = AccessibleLinkLabelButton()
      }
      return accessibleContext
    }

    protected inner class AccessibleLinkLabelButton : AccessibleLinkLabel() {
      override fun getAccessibleRole(): AccessibleRole {
        return AccessibleRole.PUSH_BUTTON
      }
    }
  }

  private inner class ManagePluginRepositoriesAction : DumbAwareAction(IdeBundle.message("plugin.manager.repositories")) {
    override fun actionPerformed(e: AnActionEvent) {
      val oldRepoUrls = ArrayList(UpdateSettings.getInstance().getStoredPluginHosts())
      if (ShowSettingsUtil.getInstance().editConfigurable(cardPanel, PluginHostsConfigurable())) {
        if (pluginManagerCustomizer == null) {
          resetPanels()
        }

          val customizer = PluginManagerCustomizer.getInstance()
        if (customizer != null) {
          val newRepoUrls = UpdateSettings.getInstance().getStoredPluginHosts()
          val addedRepoUrls = ArrayList(newRepoUrls)
          addedRepoUrls.removeAll(oldRepoUrls)
          val removedRepoUrls = ArrayList(oldRepoUrls)
          removedRepoUrls.removeAll(newRepoUrls)
          customizer.updateCustomRepositories(addedRepoUrls, removedRepoUrls) {
            resetPanels()
          }
        }
      }
    }
  }

  private inner class OpenHttpProxyConfigurableAction : DumbAwareAction(IdeBundle.message("button.http.proxy.settings")) {
    override fun actionPerformed(e: AnActionEvent) {
      if (HttpProxyConfigurable.editConfigurable(cardPanel)) {
        resetPanels()
      }
    }
  }

  private inner class ManagePluginCertificatesAction : DumbAwareAction(IdeBundle.message("plugin.manager.custom.certificates")) {
    override fun actionPerformed(e: AnActionEvent) {
      if (ShowSettingsUtil.getInstance().editConfigurable(cardPanel, PluginCertificateManager())) {
        resetPanels()
      }
    }
  }

  private inner class CustomInstallPluginFromDiskAction : InstallFromDiskAction(
    this@PluginManagerConfigurablePanel.pluginModelFacade.getModel(),
    this@PluginManagerConfigurablePanel.pluginModelFacade.getModel(),
    this@PluginManagerConfigurablePanel.cardPanel,
  ) {
    @RequiresEdt
    override fun onPluginInstalledFromDisk(callbackData: PluginInstallCallbackData, project: Project?) {
      if (pluginManagerCustomizer != null) {
        pluginManagerCustomizer.updateAfterModification {
          this@PluginManagerConfigurablePanel.onPluginInstalledFromDisk(callbackData)
        }
        return
      }
      this@PluginManagerConfigurablePanel.onPluginInstalledFromDisk(callbackData)
    }
  }

  private inner class ResetConfigurableAction : DumbAwareAction(IdeBundle.message("plugin.manager.refresh")) {
    override fun actionPerformed(e: AnActionEvent) {
      resetPanels()
    }
  }

  private inner class UpdatePluginsAutomaticallyToggleAction : DumbAwareToggleAction(IdeBundle.message("updates.plugins.autoupdate.settings.action")) {
    override fun isSelected(e: AnActionEvent): Boolean {
      return pluginsAutoUpdateEnabled!!
    }

    override fun setSelected(e: AnActionEvent, state: Boolean) {
      pluginsAutoUpdateEnabled = state
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
      return ActionUpdateThread.EDT
    }
  }

  private class GroupByActionGroup : DefaultActionGroup(), CheckedActionGroup

  private inner class ChangePluginStateAction(enable: Boolean) : DumbAwareAction(
    if (enable) IdeBundle.message("plugins.configurable.enable.all.downloaded")
    else IdeBundle.message("plugins.configurable.disable.all.downloaded"),
  ) {
    private val myEnable: Boolean = enable

    override fun actionPerformed(e: AnActionEvent) {
      PluginModelAsyncOperationsExecutor.switchPlugins(coroutineScope, pluginModelFacade, myEnable) { models ->
        //noinspection unchecked
        setState(pluginModelFacade, models as Collection<PluginUiModel>, myEnable)
      }
    }
  }

  companion object {
    private const val MARKETPLACE_TAB: Int = 0
    private const val INSTALLED_TAB: Int = 1

    @JvmStatic
    fun showRightBottomPopup(component: Component, @Nls title: String, group: ActionGroup) {
      val actions = GroupByActionGroup()
      actions.addSeparator("  " + title)
      actions.addAll(group)

      val context: DataContext = DataManager.getInstance().getDataContext(component)

      val popup: JBPopup = object : PopupFactoryImpl.ActionGroupPopup(
        null,
        null,
        actions,
        context,
        ActionPlaces.POPUP,
        PresentationFactory(),
        ActionPopupOptions.honorMnemonics(),
        null,
      ) {}
      popup.addListener(object : JBPopupListener {
        override fun beforeShown(event: LightweightWindowEvent) {
          val location = component.locationOnScreen
          val size: Dimension = popup.size
          popup.setLocation(Point(location.x + component.width - size.width, location.y + component.height))
        }
      })
      popup.show(component)
    }

    private fun getStoredSelectionTab(): Int {
      val value = PropertiesComponent.getInstance().getInt(PluginManagerConfigurable.SELECTION_TAB_KEY, MARKETPLACE_TAB)
      return if (value < MARKETPLACE_TAB || value > INSTALLED_TAB) MARKETPLACE_TAB else value
    }

    private fun storeSelectionTab(value: Int) {
      PropertiesComponent.getInstance().setValue(PluginManagerConfigurable.SELECTION_TAB_KEY, value, MARKETPLACE_TAB)
    }

    /** Modifies the state of the plugin list, excluding Ultimate plugins when the Ultimate license is not active. */
    @JvmStatic
    fun setState(pluginModelFacade: PluginModelFacade, models: Collection<PluginUiModel>, isEnable: Boolean) {
      if (models.isEmpty()) return

      val pluginsRequiringUltimate = UiPluginManager.getInstance()
        .filterPluginsRequiringUltimateButItsDisabled(models.map { it.pluginId })
      val suitableDescriptors = models.filter { descriptor -> !pluginsRequiringUltimate.contains(descriptor.pluginId) }

      if (suitableDescriptors.isEmpty()) return

      if (isEnable) {
        pluginModelFacade.enable(suitableDescriptors)
      }
      else {
        pluginModelFacade.disable(suitableDescriptors)
      }
    }

    @JvmStatic
    fun setUpdateDescriptors(panel: PluginsGroupComponent, updates: Collection<PluginUiModel>) {
      val idToUpdate = updates.associateBy { it.pluginId }
      for (group in panel.groups) {
        for (plugin in group.plugins) {
          val update = idToUpdate[plugin.getPluginDescriptor().pluginId]
          plugin.setUpdateDescriptor(update)
        }
      }
    }

    @JvmStatic
    fun registerCopyProvider(component: PluginsGroupComponent) {
      val copyProvider = object : CopyProvider {
        override fun performCopy(dataContext: DataContext) {
          val text = StringUtil.join(
            component.selection,
            { pluginComponent: ListPluginComponent ->
              val model = pluginComponent.getPluginModel()
              String.format("%s (%s)", model.name, model.version)
            },
            "\n",
          )
          CopyPasteManager.getInstance().setContents(TextTransferable(text as String?))
        }

        override fun getActionUpdateThread(): ActionUpdateThread {
          return ActionUpdateThread.EDT
        }

        override fun isCopyEnabled(dataContext: DataContext): Boolean {
          return component.selection.isNotEmpty()
        }

        override fun isCopyVisible(dataContext: DataContext): Boolean {
          return true
        }
      }

      DataManager.registerDataProvider(component) { dataId ->
        if (PlatformDataKeys.COPY_PROVIDER.`is`(dataId)) copyProvider else null
      }
    }

    @JvmStatic
    fun createScrollPane(panel: PluginsGroupComponent, initSelection: Boolean): JComponent {
      val pane = JBScrollPane(panel, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER)
      pane.border = JBUI.Borders.empty()
      if (initSelection) {
        panel.initialSelection()
      }
      return pane
    }
  }
}
