// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.icons.AllIcons
import com.intellij.ide.IdeBundle
import com.intellij.ide.plugins.PluginManagerConfigurablePanel.Companion.createScrollPane
import com.intellij.ide.plugins.PluginManagerConfigurablePanel.Companion.registerCopyProvider
import com.intellij.ide.plugins.PluginManagerConfigurablePanel.Companion.setState
import com.intellij.ide.plugins.PluginManagerConfigurablePanel.Companion.setUpdateDescriptors
import com.intellij.ide.plugins.PluginManagerConfigurablePanel.Companion.showRightBottomPopup
import com.intellij.ide.plugins.marketplace.statistics.PluginManagerUsageCollector
import com.intellij.ide.plugins.newui.ListPluginComponent
import com.intellij.ide.plugins.newui.MultiSelectionEventHandler
import com.intellij.ide.plugins.newui.MyPluginModel
import com.intellij.ide.plugins.newui.PluginUpdatesService
import com.intellij.ide.plugins.newui.PluginDetailsPageComponent
import com.intellij.ide.plugins.newui.PluginInstallationState
import com.intellij.ide.plugins.newui.PluginLogo
import com.intellij.ide.plugins.newui.PluginModelFacade
import com.intellij.ide.plugins.newui.PluginUiModel
import com.intellij.ide.plugins.newui.PluginUpdateSubscription
import com.intellij.ide.plugins.newui.PluginsGroup
import com.intellij.ide.plugins.newui.PluginsGroupComponent
import com.intellij.ide.plugins.newui.PluginsGroupComponentWithProgress
import com.intellij.ide.plugins.newui.PluginsTab
import com.intellij.ide.plugins.newui.SearchQueryParser
import com.intellij.ide.plugins.newui.SearchResultPanel
import com.intellij.ide.plugins.newui.SearchUpDownPopupController
import com.intellij.ide.plugins.newui.SearchWords
import com.intellij.ide.plugins.newui.UIPluginGroup
import com.intellij.ide.plugins.newui.UiPluginManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.KeepPopupOnPerform
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState.any
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.components.fields.ExtendableTextComponent
import com.intellij.ui.components.labels.LinkLabel
import com.intellij.ui.components.labels.LinkListener
import com.intellij.util.concurrency.annotations.RequiresEdt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import java.util.SortedSet
import java.util.function.Consumer
import java.util.function.Supplier
import javax.swing.JComponent
import javax.swing.JLabel

@ApiStatus.Internal
class InstalledPluginsTab @RequiresEdt constructor(
  private val pluginModelFacade: PluginModelFacade,
  private val coroutineScope: CoroutineScope,
  private val searchInMarketplaceTabHandler: Consumer<String>?,
  searchTextFieldQueryDebouncePeriodMs: Long = 100,
) : PluginsTab(searchTextFieldQueryDebouncePeriodMs) {
  private val installedSearchGroup = DefaultActionGroup().apply {
    for (option in InstalledSearchOption.entries) {
      add(InstalledSearchOptionAction(option))
    }
  }

  private val bundledUpdateGroup =
    PluginsGroup(IdeBundle.message("plugins.configurable.bundled.updates"), PluginsGroupType.BUNDLED_UPDATE)
  private val userInstalled = PluginsGroup(IdeBundle.message("plugins.configurable.userInstalled"), PluginsGroupType.INSTALLED)
  private val installing = PluginsGroup(IdeBundle.message("plugins.configurable.installing"), PluginsGroupType.INSTALLING)

  private val updateAllLink: LinkLabel<Any?> =
    PluginManagerConfigurablePanel.LinkLabelButton(IdeBundle.message("plugin.manager.update.all"), null)
  private val bundledUpdateAllLink: LinkLabel<Any?> =
    PluginManagerConfigurablePanel.LinkLabelButton(IdeBundle.message("plugin.manager.update.all"), null)
  private val updateCounter: JLabel = CountComponent()
  private val bundledUpdateCounter: JLabel = CountComponent()

  override val detailsPage: PluginDetailsPageComponent = createDetailsPanel(searchListener)
  override val searchPanel: InstalledPluginsTabSearchResultPanel = createSearchPanel(selectionListener)

  private val eventHandler = MultiSelectionEventHandler()
  private val installedPanel = createInstalledPanel(eventHandler)

  private var pluginUpdateSubscription: PluginUpdateSubscription? = null

  init {
    updateAllLink.isVisible = false
    bundledUpdateAllLink.isVisible = false
    updateCounter.isVisible = false
    bundledUpdateCounter.isVisible = false

    val updateAllListener = LinkListener<Any?> { _, _ -> onUpdateAllClick() }
    updateAllLink.setListener(updateAllListener, null)
    userInstalled.addSecondaryAction(updateAllLink)
    userInstalled.addSecondaryAction(updateCounter)

    bundledUpdateAllLink.setListener(updateAllListener, null)
    bundledUpdateGroup.addSecondaryAction(bundledUpdateAllLink)
    bundledUpdateGroup.addSecondaryAction(bundledUpdateCounter)

    customizeSearchTextField()
  }

  fun getInstalledPanel(): PluginsGroupComponentWithProgress = installedPanel

  fun getInstalledSearchPanel(): SearchResultPanel = searchPanel

  fun getInstalledGroups(): List<UIPluginGroup> = getInstalledPanel().groups

  @RequiresEdt
  override fun createPluginsPanel(): JComponent {
    installedPanel.showLoadingIcon()
    computeAndApplyInstalledPanelModel()
    return createScrollPane(installedPanel, true)
  }

  private fun computeAndApplyInstalledPanelModel() {
    val myPluginModel = pluginModelFacade.getModel()
    coroutineScope.launch(Dispatchers.IO) {
      myPluginModel.waitForSessionInitialization()
      val pluginManager = UiPluginManager.getInstance()
      val installedPlugins = pluginManager.getInstalledPlugins()
      val visiblePlugins = pluginManager.getVisiblePlugins(Registry.`is`("plugins.show.implementation.details"))
      val errorCheckResults = pluginManager.loadErrors(myPluginModel.mySessionId.toString())
      val visiblePluginsRequiresUltimate = pluginManager.getPluginsRequiresUltimateMap(visiblePlugins.map { it.pluginId })
      val errors = MyPluginModel.getErrors(errorCheckResults)
      val installationStates = pluginManager.getInstallationStates()
      withContext(Dispatchers.EDT + any().asContextElement()) {
        try {
          PluginLogo.startBatchMode()
          val model = CreateInstalledPanelModel(
            installedPlugins,
            visiblePlugins,
            errors,
            visiblePluginsRequiresUltimate,
            installationStates
          )
          applyInstalledPanelModel(model)
        }
        finally {
          PluginLogo.endBatchMode()
        }
      }
    }
  }

  @RequiresEdt
  private fun applyInstalledPanelModel(model: CreateInstalledPanelModel) {
    try {
      pluginModelFacade.getModel().setDownloadedGroup(installedPanel, userInstalled, installing)
      installing.getPreloadedModel().setErrors(model.errors)
      installing.getPreloadedModel().setPluginInstallationStates(model.installationStates)
      installing.addModels(MyPluginModel.installingPlugins)
      if (!installing.getModels().isEmpty()) {
        installing.sortByName()
        installing.titleWithCount()
        installedPanel.addGroup(installing)
      }

      userInstalled.getPreloadedModel().setErrors(model.errors)
      userInstalled.getPreloadedModel().setPluginInstallationStates(model.installationStates)
      userInstalled.addModels(model.installedPlugins)

      bundledUpdateGroup.getPreloadedModel().setErrors(model.errors)
      bundledUpdateGroup.getPreloadedModel().setPluginInstallationStates(model.installationStates)

      // bundled includes bundled plugin updates
      val visibleNonBundledPlugins = ArrayList<PluginUiModel>()
      val visibleBundledPlugins = ArrayList<PluginUiModel>()
      for (plugin in model.visiblePlugins) {
        if (plugin.isBundled || plugin.isBundledUpdate) {
          visibleBundledPlugins.add(plugin)
        }
        else {
          visibleNonBundledPlugins.add(plugin)
        }
      }

      val installedPluginIds = model.installedPlugins.map { it.pluginId }

      val nonBundledPlugins = ArrayList<PluginUiModel>()
      for (plugin in visibleNonBundledPlugins) {
        if (!installedPluginIds.contains(plugin.pluginId)) {
          nonBundledPlugins.add(plugin)
        }
      }

      userInstalled.addModels(nonBundledPlugins)

      val defaultCategory = IdeBundle.message("plugins.configurable.other.bundled")

      val promotionPanelSuppliers = HashMap<String, Supplier<JComponent?>>()
      if (Registry.`is`("ide.plugins.category.promotion.enabled")) {
        for (provider in PROMOTION_EP_NAME.extensionList) {
          promotionPanelSuppliers[provider.getCategoryName()] = Supplier { provider.createPromotionPanel() }
        }
      }

      val groupedVisibleBundledPlugins = HashMap<String, MutableList<PluginUiModel>>()
      for (descriptor in visibleBundledPlugins) {
        val category = StringUtil.defaultIfEmpty(descriptor.displayCategory, defaultCategory)
        val group = groupedVisibleBundledPlugins.getOrPut(category) { ArrayList() }
        group.add(descriptor)
      }

      val sortedBundledGroups = ArrayList<ComparablePluginsGroup>()
      for ((category, descriptors) in groupedVisibleBundledPlugins) {
        val group = ComparablePluginsGroup(category, descriptors, model.visiblePluginsRequiresUltimate)
        if (Registry.`is`("ide.plugins.category.promotion.enabled")) {
          val promotionPanelSupplier = promotionPanelSuppliers[category]
          if (promotionPanelSupplier != null) {
            val promotionPanel = promotionPanelSupplier.get()
            if (promotionPanel != null) {
              group.setPromotionPanel(promotionPanel)
            }
          }
        }
        sortedBundledGroups.add(group)
      }
      sortedBundledGroups.sortWith { o1, o2 ->
        if (Registry.`is`("ide.plugins.category.promotion.enabled")) {
          var isPriorityO1 = false
          var isPriorityO2 = false
          for (provider in PROMOTION_EP_NAME.extensionList) {
            if (provider.isPriorityCategory()) {
              val priorityCategory = provider.getCategoryName()
              if (priorityCategory == o1.title) {
                isPriorityO1 = true
              }
              if (priorityCategory == o2.title) {
                isPriorityO2 = true
              }
            }
          }
          if (isPriorityO1 != isPriorityO2) {
            return@sortWith if (isPriorityO1) -1 else 1
          }
        }
        if (defaultCategory == o1.title) {
          return@sortWith 1
        }
        if (defaultCategory == o2.title) {
          return@sortWith -1
        }
        o1.compareTo(o2)
      }

      if (Registry.`is`("ide.plugins.category.promotion.enabled")) {
        // Add priority groups with promotion panel before userInstalled
        for (group in sortedBundledGroups) {
          if (group.promotionPanel != null) {
            group.getPreloadedModel().setErrors(model.errors)
            group.getPreloadedModel().setPluginInstallationStates(model.installationStates)
            installedPanel.addGroup(group)
            pluginModelFacade.getModel().addEnabledGroup(group)
          }
        }
      }

      if (!userInstalled.getModels().isEmpty()) {
        userInstalled.sortByName()

        var enabledNonBundledCount = 0L
        for (descriptor in nonBundledPlugins) {
          if (!pluginModelFacade.getModel().isDisabled(descriptor.pluginId)) {
            enabledNonBundledCount++
          }
        }
        userInstalled.titleWithCount(Math.toIntExact(enabledNonBundledCount))
        if (userInstalled.ui == null) {
          installedPanel.addGroup(userInstalled)
        }
        pluginModelFacade.getModel().addEnabledGroup(userInstalled)
      }

      for (group in sortedBundledGroups) {
        if (!Registry.`is`("ide.plugins.category.promotion.enabled") || group.promotionPanel == null) {
          group.getPreloadedModel().setErrors(model.errors)
          group.getPreloadedModel().setPluginInstallationStates(model.installationStates)
          installedPanel.addGroup(group)
          pluginModelFacade.getModel().addEnabledGroup(group)
        }
      }

      pluginUpdateSubscription = PluginUpdatesService.getInstance().subscribe { updates ->
        val updateModels = updates.all.filter{ plugin -> pluginModelFacade.isEnabled(plugin) }
        setUpdateDescriptors(installedPanel, updateModels)
        setUpdateDescriptors(searchPanel.panel, updateModels)
        applyBundledUpdates(updateModels)
        selectionListener.accept(installedPanel)
        selectionListener.accept(searchPanel.panel)
      }
    }
    finally {
      installedPanel.hideLoadingIcon()
    }
  }

  override fun dispose() {
    pluginUpdateSubscription?.cancel()
    super.dispose()
  }

  private fun onUpdateAllClick() {
    updateAllLink.isEnabled = false
    bundledUpdateAllLink.isEnabled = false

    for (group in getInstalledGroups()) {
      if (group.isBundledUpdatesGroup) {
        continue
      }
      for (plugin in group.plugins) {
        plugin.updatePlugin()
      }
    }
  }

  private fun createInstalledPanel(eventHandler: MultiSelectionEventHandler): PluginsGroupComponentWithProgress {
    val installedPanel = object : PluginsGroupComponentWithProgress(eventHandler) {
      override fun createListComponent(
        model: PluginUiModel,
        group: PluginsGroup,
        listPluginModel: ListPluginModel,
      ): ListPluginComponent {
        return ListPluginComponent(pluginModelFacade, model, group, listPluginModel, searchListener, coroutineScope, false)
      }
    }
    installedPanel.setSelectionListener(selectionListener)
    installedPanel.accessibleContext.accessibleName = IdeBundle.message("plugin.manager.installed.panel.accessible.name")
    registerCopyProvider(installedPanel)
    return installedPanel
  }

  private fun customizeSearchTextField() {
    val textField = searchTextField.textEditor

    @Suppress("DialogTitleCapitalization")
    val searchOptionsText = IdeBundle.message("plugins.configurable.search.options")
    val searchFieldExtension = ExtendableTextComponent.Extension.create(
      /* defaultIcon = */ AllIcons.General.Filter,
      /* hoveredIcon = */ AllIcons.General.Filter,
      /* tooltip = */ searchOptionsText,
      /* focusable = */ true,
    ) {
      showRightBottomPopup(textField, IdeBundle.message("plugins.configurable.show"), installedSearchGroup)
    }
    textField.putClientProperty("search.extension", searchFieldExtension)
    textField.putClientProperty("JTextField.variant", null)
    textField.putClientProperty("JTextField.variant", "search")

    searchTextField.setHistoryPropertyName("InstalledPluginsSearchHistory")
  }

  private fun createDetailsPanel(searchListener: LinkListener<Any>): PluginDetailsPageComponent {
    val detailPanel = PluginDetailsPageComponent(pluginModelFacade, searchListener, false)
    pluginModelFacade.getModel().addDetailPanel(detailPanel)
    return detailPanel
  }

  private fun createSearchPanel(selectionListener: Consumer<in PluginsGroupComponent?>): InstalledPluginsTabSearchResultPanel {
    val installedController = object : SearchUpDownPopupController(searchTextField) {
      override fun getAttributes(): List<String> {
        return listOf(
          "/userInstalled",
          "/outdated",
          "/enabled",
          "/disabled",
          "/invalid",
          "/bundled",
          "/updatedBundled",
          SearchWords.VENDOR.value,
          SearchWords.TAG.value,
        )
      }

      override fun getValues(attribute: String): SortedSet<String>? {
        @Suppress("UNCHECKED_CAST")
        return if (SearchWords.VENDOR.value == attribute) {
          pluginModelFacade.getModel().vendors as SortedSet<String>?
        }
        else if (SearchWords.TAG.value == attribute) {
          pluginModelFacade.getModel().tags as SortedSet<String>?
        }
        else {
          null
        }
      }

      override fun showPopupForQuery() {
        showSearchPanel(searchTextField.text)
      }
    }

    val eventHandler = MultiSelectionEventHandler()
    installedController.setSearchResultEventHandler(eventHandler)
    installedController.setEventHandler(eventHandler)

    val panel = object : PluginsGroupComponentWithProgress(eventHandler) {
      override fun createListComponent(
        model: PluginUiModel,
        group: PluginsGroup,
        listPluginModel: ListPluginModel,
      ): ListPluginComponent {
        return ListPluginComponent(pluginModelFacade, model, group, listPluginModel, searchListener, coroutineScope, false)
      }
    }

    panel.setSelectionListener(selectionListener)
    registerCopyProvider(panel)

    val searchPanel = InstalledPluginsTabSearchResultPanel(
      coroutineScope,
      installedController,
      panel,
      installedSearchGroup,
      Supplier { installedPanel },
      selectionListener,
      if (searchInMarketplaceTabHandler == null) null else Consumer<String?> { query -> searchInMarketplaceTabHandler.accept(query!!) },
      pluginModelFacade,
    )
    return searchPanel
  }

  override fun updateMainSelection(selectionListener: Consumer<in PluginsGroupComponent?>) {
    selectionListener.accept(installedPanel)
  }

  override fun hideSearchPanel() {
    super.hideSearchPanel()
    pluginModelFacade.getModel().setInvalidFixCallback(null)
  }

  override fun onSearchReset() {
    PluginManagerUsageCollector.searchReset()
  }

  private fun handleSearchOptionSelection(updateAction: InstalledSearchOptionAction) {
    val queries = ArrayList<String>()
    object : SearchQueryParser.Installed(searchTextField.text) {
      override fun addToSearchQuery(query: String) {
        queries.add(query)
      }

      override fun handleAttribute(name: String, value: String) {
        if (!updateAction.myIsSelected) {
          queries.add(name + if (value.isEmpty()) "" else wrapAttribute(value))
        }
      }
    }

    if (updateAction.myIsSelected) {
      queries.add(updateAction.query)
    }
    else {
      queries.remove(updateAction.query)
    }

    val query = StringUtil.join(queries, " ")
    searchTextField.setTextIgnoreEvents(query)
    if (query.isEmpty()) {
      hideSearchPanel()
    }
    else {
      showSearchPanel(query)
    }
  }

  private fun applyBundledUpdates(updates: Collection<PluginUiModel>?) {
    if (updates.isNullOrEmpty()) {
      if (bundledUpdateGroup.ui != null) {
        getInstalledPanel().removeGroup(bundledUpdateGroup)
        getInstalledPanel().doLayout()
      }
    }
    else if (bundledUpdateGroup.ui == null) {
      val secondaryActions = bundledUpdateGroup.secondaryActions
      if (secondaryActions.isNullOrEmpty()) {
        // removeGroup clears actions too
        bundledUpdateGroup.addSecondaryAction(bundledUpdateAllLink)
        bundledUpdateGroup.addSecondaryAction(bundledUpdateCounter)
      }
      for (descriptor in updates) {
        for (group in getInstalledPanel().groups) {
          val component = group.findComponent(descriptor.pluginId)
          if (component != null && component.getPluginModel().isBundled) {
            bundledUpdateGroup.addModel(component.getPluginModel())
            break
          }
        }
      }
      if (!bundledUpdateGroup.getModels().isEmpty()) {
        var insertPosition = 0
        if (Registry.`is`("ide.plugins.category.promotion.enabled")) {
          val groups = getInstalledPanel().groups
          for (i in groups.indices) {
            if (groups[i].promotionPanel != null) {
              insertPosition = i + 1
              break
            }
          }
        }
        getInstalledPanel().addGroup(bundledUpdateGroup, insertPosition)
        bundledUpdateGroup.ui!!.isBundledUpdatesGroup = true

        for (descriptor in updates) {
          val component = bundledUpdateGroup.ui!!.findComponent(descriptor.pluginId)
          component?.setUpdateDescriptor(descriptor)
        }

        getInstalledPanel().doLayout()
      }
    }
    else {
      val toDelete = ArrayList<ListPluginComponent>()

      for (plugin in bundledUpdateGroup.ui!!.plugins) {
        var exist = false
        for (update in updates) {
          if (plugin.getPluginModel().pluginId == update.pluginId) {
            exist = true
            break
          }
        }
        if (!exist) {
          toDelete.add(plugin)
        }
      }

      for (component in toDelete) {
        getInstalledPanel().removeFromGroup(bundledUpdateGroup, component.getPluginModel())
      }

      for (update in updates) {
        val exist = bundledUpdateGroup.ui!!.findComponent(update.pluginId)
        if (exist != null) {
          continue
        }
        for (group in getInstalledPanel().groups) {
          if (group == bundledUpdateGroup.ui) {
            continue
          }
          val component = group.findComponent(update.pluginId)
          if (component != null && component.getPluginModel().isBundled) {
            getInstalledPanel().addToGroup(bundledUpdateGroup, component.getPluginModel())
            break
          }
        }
      }

      if (bundledUpdateGroup.getModels().isEmpty()) {
        getInstalledPanel().removeGroup(bundledUpdateGroup)
      }
      else {
        for (descriptor in updates) {
          val component = bundledUpdateGroup.ui!!.findComponent(descriptor.pluginId)
          component?.setUpdateDescriptor(descriptor)
        }
      }

      getInstalledPanel().doLayout()
    }

    updateAllLink.isVisible = updateAllLink.isVisible && bundledUpdateGroup.ui == null
    updateCounter.isVisible = updateCounter.isVisible && bundledUpdateGroup.ui == null
  }

  fun onPluginUpdatesRecalculation(updatesCount: Int?, tooltip: @Nls String?) {
    val count = updatesCount ?: 0
    val text = count.toString()
    val visible = count > 0

    updateAllLink.isEnabled = true
    bundledUpdateAllLink.isEnabled = true
    updateAllLink.isVisible = visible && bundledUpdateGroup.ui == null
    bundledUpdateAllLink.isVisible = visible

    updateCounter.text = text
    updateCounter.toolTipText = tooltip
    bundledUpdateCounter.text = text
    bundledUpdateCounter.toolTipText = tooltip
    updateCounter.isVisible = visible && bundledUpdateGroup.ui == null
    bundledUpdateCounter.isVisible = visible
  }

  internal inner class InstalledSearchOptionAction(private val myOption: InstalledSearchOption)
    : ToggleAction(myOption.myPresentableNameSupplier), DumbAware {
    var myIsSelected: Boolean = false

    init {
      templatePresentation.setKeepPopupOnPerform(KeepPopupOnPerform.IfRequested)
    }

    override fun isSelected(e: AnActionEvent): Boolean {
      return myIsSelected
    }

    override fun setSelected(e: AnActionEvent, state: Boolean) {
      myIsSelected = state
      handleSearchOptionSelection(this)
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
      return ActionUpdateThread.BGT
    }

    fun setState(parser: SearchQueryParser.Installed?) {
      if (parser == null) {
        myIsSelected = false
        return
      }

      myIsSelected = when (myOption) {
        InstalledSearchOption.Enabled -> parser.enabled
        InstalledSearchOption.Disabled -> parser.disabled
        InstalledSearchOption.UserInstalled -> parser.userInstalled
        InstalledSearchOption.Bundled -> parser.bundled
        InstalledSearchOption.UpdatedBundled -> parser.updatedBundled
        InstalledSearchOption.Invalid -> parser.invalid
        InstalledSearchOption.NeedUpdate -> parser.needUpdate
      }
    }

    val query: String
      get() {
        return if (myOption == InstalledSearchOption.NeedUpdate) "/outdated" else "/" + StringUtil.decapitalize(myOption.name)
      }
  }

  private inner class ComparablePluginsGroup(
    category: @NlsSafe String,
    descriptors: List<PluginUiModel>,
    private val myPluginsRequiresUltimateButItsDisabled: Map<PluginId, Boolean>,
  ) : PluginsGroup(category, PluginsGroupType.INSTALLED), Comparable<ComparablePluginsGroup> {
    private var myIsEnable = false

    init {
      addModels(descriptors)
      sortByName()

      mainAction = PluginManagerConfigurablePanel.LinkLabelButton("", null) { _, _ -> setEnabledState() }
      val hasPluginsAvailableForEnableDisable =
        descriptors.any { !myPluginsRequiresUltimateButItsDisabled[it.pluginId]!! }
      mainAction!!.isVisible = hasPluginsAvailableForEnableDisable
      titleWithEnabled(pluginModelFacade)
    }

    override fun titleWithEnabled(pluginModelFacade: PluginModelFacade) {
      var enabled = 0
      for (descriptor in models) {
        if (pluginModelFacade.isLoaded(descriptor) &&
            pluginModelFacade.isEnabled(descriptor) &&
            !myPluginsRequiresUltimateButItsDisabled.getOrDefault(descriptor.pluginId, false) &&
            !descriptor.isIncompatible
        ) {
          enabled++
        }
      }
      titleWithCount(enabled)
    }

    override fun compareTo(other: ComparablePluginsGroup): Int {
      return StringUtil.compare(title, other.title, true)
    }

    override fun titleWithCount(enabled: Int) {
      myIsEnable = enabled == 0
      val key = if (myIsEnable) "plugins.configurable.enable.all" else "plugins.configurable.disable.all"
      mainAction!!.text = IdeBundle.message(key)
    }

    private fun setEnabledState() {
      setState(pluginModelFacade, models, myIsEnable)
    }
  }

  internal enum class InstalledSearchOption(val myPresentableNameSupplier: Supplier<String>) {
    UserInstalled(IdeBundle.messagePointer("plugins.configurable.InstalledSearchOption.UserInstalled")),
    NeedUpdate(IdeBundle.messagePointer("plugins.configurable.InstalledSearchOption.NeedUpdate")),
    Enabled(IdeBundle.messagePointer("plugins.configurable.InstalledSearchOption.Enabled")),
    Disabled(IdeBundle.messagePointer("plugins.configurable.InstalledSearchOption.Disabled")),
    Invalid(IdeBundle.messagePointer("plugins.configurable.InstalledSearchOption.Invalid")),
    Bundled(IdeBundle.messagePointer("plugins.configurable.InstalledSearchOption.Bundled")),
    UpdatedBundled(IdeBundle.messagePointer("plugins.configurable.InstalledSearchOption.UpdatedBundled")),
  }

  private companion object {
    val PROMOTION_EP_NAME: ExtensionPointName<PluginCategoryPromotionProvider> =
      ExtensionPointName.create("com.intellij.pluginCategoryPromotionProvider")
  }
}

private data class CreateInstalledPanelModel(
  val installedPlugins: List<PluginUiModel>,
  val visiblePlugins: List<PluginUiModel>,
  val errors: Map<PluginId, List<HtmlChunk>>,
  val visiblePluginsRequiresUltimate: Map<PluginId, Boolean>,
  val installationStates: Map<PluginId, PluginInstallationState>,
)