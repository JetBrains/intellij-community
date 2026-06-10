// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.execution.process.ProcessIOExecutorService
import com.intellij.featureStatistics.FeatureUsageTracker
import com.intellij.ide.IdeBundle
import com.intellij.ide.impl.ProjectUtil
import com.intellij.ide.plugins.PluginManagerConfigurablePanel.Companion.createScrollPane
import com.intellij.ide.plugins.PluginManagerConfigurablePanel.Companion.registerCopyProvider
import com.intellij.ide.plugins.PluginManagerConfigurablePanel.Companion.setUpdateDescriptors
import com.intellij.ide.plugins.marketplace.PluginSearchResult
import com.intellij.ide.plugins.marketplace.statistics.PluginManagerUsageCollector
import com.intellij.ide.plugins.newui.ListPluginComponent
import com.intellij.ide.plugins.newui.MultiSelectionEventHandler
import com.intellij.ide.plugins.newui.MyPluginModel
import com.intellij.ide.plugins.newui.PluginUpdatesService
import com.intellij.ide.plugins.newui.NoOpPluginsViewCustomizer
import com.intellij.ide.plugins.newui.PluginDetailsPageComponent
import com.intellij.ide.plugins.newui.PluginInstallationState
import com.intellij.ide.plugins.newui.PluginLogo
import com.intellij.ide.plugins.newui.PluginManagerCustomizer
import com.intellij.ide.plugins.newui.PluginModelFacade
import com.intellij.ide.plugins.newui.PluginUiModel
import com.intellij.ide.plugins.newui.PluginUiModelAdapter
import com.intellij.ide.plugins.newui.PluginUpdateSubscription
import com.intellij.ide.plugins.newui.PluginsGroup
import com.intellij.ide.plugins.newui.PluginsGroupComponent
import com.intellij.ide.plugins.newui.PluginsGroupComponentWithProgress
import com.intellij.ide.plugins.newui.PluginsTab
import com.intellij.ide.plugins.newui.PluginsViewCustomizer
import com.intellij.ide.plugins.newui.SearchPopup
import com.intellij.ide.plugins.newui.SearchQueryParser
import com.intellij.ide.plugins.newui.SearchResultPanel
import com.intellij.ide.plugins.newui.SearchUpDownPopupController
import com.intellij.ide.plugins.newui.SearchWords
import com.intellij.ide.plugins.newui.UiPluginManager
import com.intellij.ide.plugins.newui.getPluginsViewCustomizer
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.KeepPopupOnPerform
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ModalityState.any
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.FUSEventSource
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.findSuggestedPlugins
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.labels.LinkListener
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.StatusText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import java.io.IOException
import java.util.function.Consumer
import java.util.function.Predicate
import java.util.function.Supplier
import javax.swing.JComponent

@ApiStatus.Internal
internal class MarketplacePluginsTab @RequiresEdt constructor(
  facade: PluginModelFacade,
  scope: CoroutineScope,
  customizer: PluginManagerCustomizer?,
  searchTextFieldQueryDebouncePeriodMs: Long = 250,
) : PluginsTab(searchTextFieldQueryDebouncePeriodMs) {
  private val pluginModelFacade: PluginModelFacade = facade
  private val coroutineScope: CoroutineScope = scope
  private val pluginManagerCustomizer: PluginManagerCustomizer? = customizer
  private var pluginUpdateSubscription: PluginUpdateSubscription? = null

  private val marketplaceSortByGroup: DefaultActionGroup = DefaultActionGroup().apply {
    for (option in MarketplaceTabSearchSortByOptions.entries) {
      addAction(MarketplaceSortByAction(option))
    }
  }

  private var tagsSorted: List<String>? = null
  private var vendorsSorted: List<String>? = null

  override val detailsPage: PluginDetailsPageComponent = createDetailsPanel(searchListener)
  override val searchPanel: SearchResultPanel = createSearchPanel(selectionListener)

  private val eventHandler = MultiSelectionEventHandler()
  private val marketplacePanel = createMarketplacePanel(eventHandler)

  init {
    customizeSearchTextField()
  }

  @RequiresEdt
  override fun createPluginsPanel(): JComponent {
    val project = ProjectUtil.getActiveProject()
    computeAndApplyMarketplacePanelModel(selectionListener, project)
    return createScrollPane(marketplacePanel, false)
  }

  override fun updateMainSelection(selectionListener: Consumer<in PluginsGroupComponent?>) {
    selectionListener.accept(marketplacePanel)
  }

  fun resetCache() {
    tagsSorted = null
    vendorsSorted = null
  }

  private fun createMarketplacePanel(eventHandler: MultiSelectionEventHandler): PluginsGroupComponentWithProgress {
    val marketplacePanel = object : PluginsGroupComponentWithProgress(eventHandler) {
      override fun createListComponent(
        model: PluginUiModel,
        group: PluginsGroup,
        listPluginModel: ListPluginModel,
      ): ListPluginComponent {
        return ListPluginComponent(
          pluginModelFacade,
          model,
          group,
          listPluginModel,
          searchListener,
          coroutineScope,
          true,
        )
      }
    }
    marketplacePanel.setSelectionListener(selectionListener)
    marketplacePanel.getAccessibleContext().setAccessibleName(IdeBundle.message("plugin.manager.marketplace.panel.accessible.name"))
    registerCopyProvider(marketplacePanel)

    marketplacePanel.getEmptyText().setText(IdeBundle.message("plugins.configurable.marketplace.plugins.not.loaded"))
      .appendSecondaryText(IdeBundle.message("message.check.the.internet.connection.and") + " ", StatusText.DEFAULT_ATTRIBUTES, null)
      .appendSecondaryText(
        IdeBundle.message("message.link.refresh"),
        SimpleTextAttributes.LINK_PLAIN_ATTRIBUTES,
      ) { reloadMarketplaceTab() }

    return marketplacePanel
  }

  private fun computeAndApplyMarketplacePanelModel(selectionListener: Consumer<in PluginsGroupComponent?>, project: Project?) {
    val myPluginModel = pluginModelFacade.getModel()
    coroutineScope.launch(Dispatchers.IO) {
      myPluginModel.waitForSessionInitialization()
      val customRepositoriesMap = UiPluginManager.getInstance().getCustomRepositoryPluginMap()
      val suggestedPlugins = if (project != null) findSuggestedPlugins(project, customRepositoriesMap) else emptyList()
      val pluginManager = UiPluginManager.getInstance()
      val marketplaceData = mutableMapOf<String, PluginSearchResult>()
      val internalPluginsGroupDescriptor = getPluginsViewCustomizer().getInternalPluginsGroupDescriptor()
      val installationStates = pluginManager.getInstallationStates()

      val queries = listOf(
        "is_featured_search=true",
        "orderBy=update+date",
        "orderBy=downloads",
        "orderBy=rating"
      )

      val errorCheckResults = pluginManager.loadErrors(myPluginModel.mySessionId.toString())
      val errors = MyPluginModel.getErrors(errorCheckResults)
      try {
        for (query in queries) {
          val result = pluginManager.executeMarketplaceQuery(query, 18, false)
          marketplaceData[query] = result
        }
      }
      catch (e: Exception) {
        LOG.info("Main plugin repository is not available (${e.message}). Please check your network settings.")
      }
      val pluginIds = marketplaceData.flatMap { it.value.getPlugins().map { plugin -> plugin.pluginId } }.toSet() +
                      customRepositoriesMap.flatMap { it.value.map { plugin -> plugin.pluginId } }.toSet()
      val installedPlugins = pluginManager.findInstalledPlugins(pluginIds)
      withContext(Dispatchers.EDT + any().asContextElement()) {
        val model = CreateMarketplacePanelModel(
          marketplaceData,
          errors,
          suggestedPlugins,
          customRepositoriesMap,
          installedPlugins,
          installationStates,
          internalPluginsGroupDescriptor
        )
        applyMarketplacePanelModel(project, model, selectionListener)
      }
    }
  }

  @RequiresEdt
  private fun applyMarketplacePanelModel(
    project: Project?,
    model: CreateMarketplacePanelModel,
    selectionListener: Consumer<in PluginsGroupComponent?>,
  ) {
    val groups = ArrayList<PluginsGroup>()
    try {
      try {
        if (project != null) {
          addSuggestedGroup(
            groups,
            model.errors,
            model.suggestedPlugins,
            model.installedPlugins,
            model.installationStates,
          )
        }
        val internalPluginsGroupDescriptor: PluginsViewCustomizer.PluginsGroupDescriptor? = model.internalPluginsGroupDescriptor
        if (internalPluginsGroupDescriptor != null) {
          val customPlugins: List<PluginUiModel> = internalPluginsGroupDescriptor.plugins.map { PluginUiModelAdapter(it) }
          addGroup(
            groups,
            internalPluginsGroupDescriptor.name,
            PluginsGroupType.INTERNAL,
            SearchWords.INTERNAL.value,
            customPlugins,
            Predicate { customPlugins.size >= ITEMS_PER_GROUP },
            model.errors,
            model.installedPlugins,
            model.installationStates,
          )
        }

        val marketplaceData = model.marketplaceData
        addGroupViaLightDescriptor(
          groups,
          IdeBundle.message("plugins.configurable.staff.picks"),
          PluginsGroupType.STAFF_PICKS,
          "is_featured_search=true",
          SearchWords.STAFF_PICKS.value,
          marketplaceData,
          model.errors,
          model.installedPlugins,
          model.installationStates,
        )
        addGroupViaLightDescriptor(
          groups,
          IdeBundle.message("plugins.configurable.new.and.updated"),
          PluginsGroupType.NEW_AND_UPDATED,
          "orderBy=update+date",
          "/sortBy:updated",
          marketplaceData,
          model.errors,
          model.installedPlugins,
          model.installationStates,
        )
        addGroupViaLightDescriptor(
          groups,
          IdeBundle.message("plugins.configurable.top.downloads"),
          PluginsGroupType.TOP_DOWNLOADS,
          "orderBy=downloads",
          "/sortBy:downloads",
          marketplaceData,
          model.errors,
          model.installedPlugins,
          model.installationStates,
        )
        addGroupViaLightDescriptor(
          groups,
          IdeBundle.message("plugins.configurable.top.rated"),
          PluginsGroupType.TOP_RATED,
          "orderBy=rating",
          "/sortBy:rating",
          marketplaceData,
          model.errors,
          model.installedPlugins,
          model.installationStates,
        )
      }
      catch (e: IOException) {
        LOG.info("Main plugin repository is not available ('" + e.message + "'). Please check your network settings.")
      }

      for (host in RepositoryHelper.getCustomPluginRepositoryHosts()) {
        val allDescriptors = model.customRepositories[host]
        if (allDescriptors != null) {
          val groupName = IdeBundle.message("plugins.configurable.repository.0", host)
          LOG.info("Marketplace tab: '" + groupName + "' group load started")
          addGroup(
            groups,
            groupName,
            PluginsGroupType.CUSTOM_REPOSITORY,
            "/repository:\"" + host + "\"",
            allDescriptors,
            Predicate { group ->
              PluginsGroup.sortByName(group.getModels())
              allDescriptors.size > ITEMS_PER_GROUP
            },
            model.errors,
            model.installedPlugins,
            model.installationStates,
          )
        }
      }
      if (pluginManagerCustomizer != null) {
        pluginManagerCustomizer.ensurePluginStatesLoaded()
      }
    }
    finally {
      ApplicationManager.getApplication().invokeLater({
                                                        marketplacePanel.hideLoadingIcon()
                                                        try {
                                                          PluginLogo.startBatchMode()

                                                          for (group in groups) {
                                                            marketplacePanel.addGroup(group)
                                                          }
                                                        }
                                                        finally {
                                                          PluginLogo.endBatchMode()
                                                        }
                                                        marketplacePanel.doLayout()
                                                        marketplacePanel.initialSelection()

                                                        pluginUpdateSubscription = PluginUpdatesService.getInstance().subscribe { updates ->
                                                          val updateModels: List<PluginUiModel> = updates.all.filter { plugin -> pluginModelFacade.isEnabled(plugin) }
                                                          setUpdateDescriptors(marketplacePanel, updateModels)
                                                          setUpdateDescriptors(searchPanel.panel, updateModels)
                                                          selectionListener.accept(marketplacePanel)
                                                          selectionListener.accept(searchPanel.panel)
                                                        }
                                                      }, ModalityState.any())
    }
  }

  private fun createDetailsPanel(searchListener: LinkListener<Any>): PluginDetailsPageComponent {
    val detailPanel = PluginDetailsPageComponent(pluginModelFacade, searchListener, true)
    pluginModelFacade.getModel().addDetailPanel(detailPanel)
    return detailPanel
  }

  private fun createSearchPanel(selectionListener: Consumer<in PluginsGroupComponent?>): SearchResultPanel {
    val marketplaceController = object : SearchUpDownPopupController(searchTextField) {
      override fun getAttributes(): List<String> {
        val attributes = ArrayList<String>()
        attributes.add(SearchWords.TAG.value)
        attributes.add(SearchWords.SORT_BY.value)
        attributes.add(SearchWords.VENDOR.value)
        if (!RepositoryHelper.getCustomPluginRepositoryHosts().isEmpty()) {
          attributes.add(SearchWords.REPOSITORY.value)
        }
        attributes.add(SearchWords.STAFF_PICKS.value)
        attributes.add(SearchWords.SUGGESTED.value)
        if (getPluginsViewCustomizer() != NoOpPluginsViewCustomizer) {
          attributes.add(SearchWords.INTERNAL.value)
        }
        return attributes
      }

      override fun getValues(attribute: String): List<String>? {
        val word = SearchWords.find(attribute)
        return when (word) {
          SearchWords.TAG -> getOrCalculateTags()
          SearchWords.SORT_BY -> listOf(
            MarketplaceTabSearchSortByOptions.DOWNLOADS,
            MarketplaceTabSearchSortByOptions.NAME,
            MarketplaceTabSearchSortByOptions.RATING,
            MarketplaceTabSearchSortByOptions.UPDATE_DATE,
          ).map { sort -> sort.query }
          SearchWords.VENDOR -> getOrCalculateVendors()
          SearchWords.REPOSITORY -> RepositoryHelper.getCustomPluginRepositoryHosts()
          SearchWords.INTERNAL, SearchWords.SUGGESTED, SearchWords.STAFF_PICKS, null -> null
        }
      }

      override fun showPopupForQuery() {
        showSearchPanel(searchTextField.text)
      }

      override fun handleEnter() {
        if (!searchTextField.text.isEmpty()) {
          handleTrigger("marketplace.suggest.popup.enter")
        }
      }

      override fun handlePopupListFirstSelection() {
        handleTrigger("marketplace.suggest.popup.select")
      }

      private fun handleTrigger(@NonNls key: String) {
        if (myPopup != null && myPopup.type == SearchPopup.Type.SearchQuery) {
          FeatureUsageTracker.getInstance().triggerFeatureUsed(key)
        }
      }
    }

    val eventHandler = MultiSelectionEventHandler()
    marketplaceController.setSearchResultEventHandler(eventHandler)
    marketplaceController.setEventHandler(eventHandler)

    val panel = object : PluginsGroupComponentWithProgress(eventHandler) {
      override fun createListComponent(
        model: PluginUiModel,
        group: PluginsGroup,
        listPluginModel: ListPluginModel,
      ): ListPluginComponent {
        return ListPluginComponent(
          pluginModelFacade,
          model,
          group,
          listPluginModel,
          searchListener,
          coroutineScope,
          true,
        )
      }
    }

    panel.setSelectionListener(selectionListener)
    registerCopyProvider(panel)

    val project = ProjectUtil.getActiveProject()

    val searchPanel = MarketplacePluginsTabSearchResultPanel(
      coroutineScope,
      marketplaceController,
      panel,
      project,
      selectionListener,
      marketplaceSortByGroup,
      Supplier { marketplacePanel },
    )
    return searchPanel
  }

  private fun reloadMarketplaceTab() {
    val project = ProjectUtil.getActiveProject()
    marketplacePanel.clear()
    marketplacePanel.showLoadingIcon()
    computeAndApplyMarketplacePanelModel(selectionListener, project)
  }

  private fun customizeSearchTextField() {
    searchTextField.setHistoryPropertyName("MarketplacePluginsSearchHistory")
  }

  private fun getOrCalculateVendors(): List<String> {
    if (vendorsSorted == null ||
        vendorsSorted!!.isEmpty() // FIXME seems like it shouldn't be here...
    ) {
      val vendors = LinkedHashSet<String>()
      try {
        ProcessIOExecutorService.INSTANCE.submit {
          vendors.addAll(UiPluginManager.getInstance().getAllVendors())
        }.get()
      }
      catch (e: InterruptedException) {
        LOG.error("Error while getting vendors from marketplace", e)
      }
      catch (e: java.util.concurrent.ExecutionException) {
        LOG.error("Error while getting vendors from marketplace", e)
      }
      vendorsSorted = ArrayList(vendors)
    }
    return vendorsSorted!!
  }

  private fun getOrCalculateTags(): List<String> {
    if (tagsSorted == null ||
        tagsSorted!!.isEmpty() // FIXME seems like it shouldn't be here...
    ) {
      val allTags = HashSet<String>()
      val customRepoTags = UiPluginManager.getInstance().getCustomRepoTags()
      if (!customRepoTags.isEmpty()) {
        allTags.addAll(customRepoTags)
      }
      try {
        ProcessIOExecutorService.INSTANCE.submit {
          allTags.addAll(UiPluginManager.getInstance().getAllPluginsTags())
        }.get()
      }
      catch (e: InterruptedException) {
        LOG.error("Error while getting tags from marketplace", e)
      }
      catch (e: java.util.concurrent.ExecutionException) {
        LOG.error("Error while getting tags from marketplace", e)
      }
      tagsSorted = ContainerUtil.sorted(allTags, String.CASE_INSENSITIVE_ORDER)
    }
    return tagsSorted!!
  }

  private fun handleSortByOptionSelection(updateAction: MarketplaceSortByAction) {
    var removeAction: MarketplaceSortByAction? = null
    var addAction: MarketplaceSortByAction? = null

    if (updateAction.myState) {
      for (action in marketplaceSortByGroup.getChildren(ActionManager.getInstance())) {
        val sortByAction = action as MarketplaceSortByAction
        if (sortByAction !== updateAction && sortByAction.myState) {
          sortByAction.myState = false
          removeAction = sortByAction
          break
        }
      }
      addAction = updateAction
    }
    else {
      if (updateAction.myOption == MarketplaceTabSearchSortByOptions.RELEVANCE) {
        updateAction.myState = true
        return
      }

      for (action in marketplaceSortByGroup.getChildren(ActionManager.getInstance())) {
        val sortByAction = action as MarketplaceSortByAction
        if (sortByAction.myOption == MarketplaceTabSearchSortByOptions.RELEVANCE) {
          sortByAction.myState = true
          break
        }
      }

      removeAction = updateAction
    }

    val queries = ArrayList<String>()
    object : SearchQueryParser.Marketplace(searchTextField.text) { // FIXME: it's unused - why hasn't it been removed?
      override fun addToSearchQuery(query: String) {
        queries.add(query)
      }

      override fun handleAttribute(name: String, value: String) {
        queries.add(name + wrapAttribute(value))
      }
    }
    if (removeAction != null) {
      val query = removeAction.getQuery()
      if (query != null) {
        queries.remove(query)
      }
    }
    if (addAction != null) {
      val query = addAction.getQuery()
      if (query != null) {
        queries.add(query)
      }
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

  override fun onSearchReset() {
    PluginManagerUsageCollector.searchReset()
  }

  private fun addSuggestedGroup(
    groups: MutableList<PluginsGroup>,
    errors: Map<PluginId, List<HtmlChunk>>,
    plugins: List<PluginUiModel>,
    installedPlugins: Map<PluginId, PluginUiModel>,
    installationStates: Map<PluginId, PluginInstallationState>,
  ) {
    val groupName = IdeBundle.message("plugins.configurable.suggested")
    LOG.info("Marketplace tab: '" + groupName + "' group load started")

    for (plugin in plugins) {
      if (plugin.isFromMarketplace) {
        plugin.installSource = FUSEventSource.PLUGINS_SUGGESTED_GROUP
      }

      FUSEventSource.PLUGINS_SUGGESTED_GROUP.logPluginSuggested(pluginId = plugin.pluginId)
    }
    addGroup(
      groups,
      groupName,
      PluginsGroupType.SUGGESTED,
      "",
      plugins,
      Predicate { false },
      errors,
      installedPlugins,
      installationStates,
    )
  }

  private fun addGroup(
    groups: MutableList<PluginsGroup>,
    name: @Nls String,
    type: PluginsGroupType,
    showAllQuery: String,
    customPlugins: List<PluginUiModel>,
    showAllPredicate: Predicate<PluginsGroup>,
    errors: Map<PluginId, List<HtmlChunk>>,
    installedPlugins: Map<PluginId, PluginUiModel>,
    installationStates: Map<PluginId, PluginInstallationState>,
  ) {
    val group = PluginsGroup(name, type)
    group.getPreloadedModel().setErrors(errors)
    group.getPreloadedModel().setInstalledPlugins(installedPlugins)
    group.getPreloadedModel().setPluginInstallationStates(installationStates)
    var i = 0
    val iterator = customPlugins.iterator()
    while (iterator.hasNext() && i < ITEMS_PER_GROUP) {
      group.addModel(iterator.next())
      i++
    }

    if (showAllPredicate.test(group)) {
      group.mainAction = PluginManagerConfigurablePanel.LinkLabelButton<Any?>(
        IdeBundle.message("plugins.configurable.show.all"),
        null,
        searchListener,
        showAllQuery,
      )
      group.mainAction!!.setBorder(JBUI.Borders.emptyRight(5))
    }

    if (!group.getModels().isEmpty()) {
      groups.add(group)
    }
    LOG.info("Marketplace tab: '" + name + "' group load finished")
  }

  @Throws(IOException::class)
  private fun addGroupViaLightDescriptor(
    groups: MutableList<PluginsGroup>,
    name: @Nls String,
    type: PluginsGroupType,
    query: @NonNls String,
    showAllQuery: @NonNls String,
    marketplaceData: Map<String, PluginSearchResult>,
    errors: Map<PluginId, List<HtmlChunk>>,
    installedPluginIds: Map<PluginId, PluginUiModel>,
    installationStates: Map<PluginId, PluginInstallationState>,
  ) {
    LOG.info("Marketplace tab: '" + name + "' group load started")
    val searchResult = marketplaceData[query]!!
    val error = searchResult.error
    if (error != null) {
      throw IOException(error)
    }

    val plugins = searchResult.getPlugins()
    for (plugin in plugins) {
      plugin.installSource = FUSEventSource.PLUGINS_STAFF_PICKS_GROUP
      FUSEventSource.PLUGINS_STAFF_PICKS_GROUP.logPluginSuggested(pluginId = plugin.pluginId)
    }

    addGroup(
      groups,
      name,
      type,
      showAllQuery,
      plugins,
      Predicate { plugins.size >= ITEMS_PER_GROUP },
      errors,
      installedPluginIds,
      installationStates,
    )
  }

  override fun dispose() {
    marketplacePanel.dispose()
    searchPanel.dispose()
    pluginUpdateSubscription?.cancel()
    super.dispose()
  }

  fun onPanelReset(isMarketplaceTabSelected: Boolean) {
    if (isMarketplaceTabSelected) {
      reloadMarketplaceTab()
    }
    else {
      marketplacePanel.setOnBecomingVisibleCallback(::reloadMarketplaceTab)
    }
  }

  @ApiStatus.Internal
  inner class MarketplaceSortByAction(option: MarketplaceTabSearchSortByOptions) : ToggleAction(option.presentableNameSupplier), DumbAware {
    val myOption: MarketplaceTabSearchSortByOptions = option
    var myState: Boolean = false
    private var myVisible: Boolean = false

    init {
      templatePresentation.setKeepPopupOnPerform(KeepPopupOnPerform.IfRequested)
    }

    override fun update(e: AnActionEvent) {
      super.update(e)
      e.presentation.setVisible(myVisible)
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
      return ActionUpdateThread.BGT
    }

    override fun isSelected(e: AnActionEvent): Boolean {
      return myState
    }

    override fun setSelected(e: AnActionEvent, state: Boolean) {
      myState = state
      handleSortByOptionSelection(this)
    }

    fun setState(parser: SearchQueryParser.Marketplace) {
      if (myOption == MarketplaceTabSearchSortByOptions.RELEVANCE) {
        myState = parser.sortBy == null
        myVisible = parser.sortBy == null || !parser.tags.isEmpty() || !parser.vendors.isEmpty() || parser.searchQuery != null
      }
      else {
        myState = parser.sortBy != null && myOption == parser.sortBy
        myVisible = true
      }
    }

    fun getQuery(): String? {
      if (myOption == MarketplaceTabSearchSortByOptions.RELEVANCE) {
        return null
      }
      return SearchWords.SORT_BY.value + myOption.query
    }
  }

  companion object {
    private val LOG = Logger.getInstance(MarketplacePluginsTab::class.java)

    private const val ITEMS_PER_GROUP = 9
  }
}

private data class CreateMarketplacePanelModel(
  val marketplaceData: Map<String, PluginSearchResult>,
  val errors: Map<PluginId, List<HtmlChunk>>,
  val suggestedPlugins: List<PluginUiModel>,
  val customRepositories: Map<String, List<PluginUiModel>>,
  val installedPlugins: Map<PluginId, PluginUiModel>,
  val installationStates: Map<PluginId, PluginInstallationState>,
  val internalPluginsGroupDescriptor: PluginsViewCustomizer.PluginsGroupDescriptor?,
)