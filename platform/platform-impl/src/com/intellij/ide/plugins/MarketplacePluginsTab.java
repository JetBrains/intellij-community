// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins;

import com.intellij.execution.process.ProcessIOExecutorService;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.impl.ProjectUtil;
import com.intellij.ide.plugins.marketplace.PluginSearchResult;
import com.intellij.ide.plugins.marketplace.statistics.PluginManagerUsageCollector;
import com.intellij.ide.plugins.newui.ListPluginComponent;
import com.intellij.ide.plugins.newui.MultiSelectionEventHandler;
import com.intellij.ide.plugins.newui.NoOpPluginsViewCustomizer;
import com.intellij.ide.plugins.newui.PluginDetailsPageComponent;
import com.intellij.ide.plugins.newui.PluginInstallationState;
import com.intellij.ide.plugins.newui.PluginLogo;
import com.intellij.ide.plugins.newui.PluginManagerCustomizer;
import com.intellij.ide.plugins.newui.PluginModelFacade;
import com.intellij.ide.plugins.newui.PluginUiModel;
import com.intellij.ide.plugins.newui.PluginUiModelAdapter;
import com.intellij.ide.plugins.newui.PluginsGroup;
import com.intellij.ide.plugins.newui.PluginsGroupComponent;
import com.intellij.ide.plugins.newui.PluginsGroupComponentWithProgress;
import com.intellij.ide.plugins.newui.PluginsTab;
import com.intellij.ide.plugins.newui.PluginsViewCustomizer;
import com.intellij.ide.plugins.newui.PluginsViewCustomizerKt;
import com.intellij.ide.plugins.newui.SearchPopup;
import com.intellij.ide.plugins.newui.SearchQueryParser;
import com.intellij.ide.plugins.newui.SearchResultPanel;
import com.intellij.ide.plugins.newui.SearchUpDownPopupController;
import com.intellij.ide.plugins.newui.SearchWords;
import com.intellij.ide.plugins.newui.UiPluginManager;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.KeepPopupOnPerform;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.FUSEventSource;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.components.labels.LinkListener;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.StatusText;
import kotlinx.coroutines.CoroutineScope;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.JComponent;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.function.Predicate;

import static com.intellij.ide.plugins.PluginManagerConfigurablePanel.applyUpdates;
import static com.intellij.ide.plugins.PluginManagerConfigurablePanel.clearUpdates;
import static com.intellij.ide.plugins.PluginManagerConfigurablePanel.createScrollPane;
import static com.intellij.ide.plugins.PluginManagerConfigurablePanel.registerCopyProvider;

@ApiStatus.Internal
class MarketplacePluginsTab extends PluginsTab {
  private static final Logger LOG = Logger.getInstance(MarketplacePluginsTab.class);

  private static final int ITEMS_PER_GROUP = 9;

  private final @NotNull PluginModelFacade myPluginModelFacade;
  private final @NotNull CoroutineScope myCoroutineScope;
  private final @Nullable PluginManagerCustomizer myPluginManagerCustomizer;

  private PluginsGroupComponentWithProgress myMarketplacePanel;
  private SearchResultPanel myMarketplaceSearchPanel;
  private Runnable myMarketplaceRunnable;

  private final DefaultActionGroup myMarketplaceSortByGroup;

  private List<String> myTagsSorted;
  private List<String> myVendorsSorted;

  MarketplacePluginsTab(
    @NotNull PluginModelFacade facade,
    @NotNull CoroutineScope scope,
    @Nullable PluginManagerCustomizer customizer
  ) {
    super();
    myPluginModelFacade = facade;
    myCoroutineScope = scope;
    myPluginManagerCustomizer = customizer;

    myMarketplaceSortByGroup = new DefaultActionGroup();
    for (MarketplaceTabSearchSortByOptions option : MarketplaceTabSearchSortByOptions.getEntries()) {
      myMarketplaceSortByGroup.addAction(new MarketplaceSortByAction(option));
    }

    myTagsSorted = null;
    myVendorsSorted = null;
  }

  protected void resetCache() {
    myTagsSorted = null;
    myVendorsSorted = null;
  }

  @Override
  protected void createSearchTextField(int flyDelay) {
    super.createSearchTextField(250);
    searchTextField.setHistoryPropertyName("MarketplacePluginsSearchHistory");
  }

  @Override
  protected @NotNull PluginDetailsPageComponent createDetailsPanel(@NotNull LinkListener<Object> searchListener) {
    PluginDetailsPageComponent detailPanel = new PluginDetailsPageComponent(myPluginModelFacade, searchListener, true);
    myPluginModelFacade.getModel().addDetailPanel(detailPanel);
    return detailPanel;
  }

  @Override
  protected @NotNull JComponent createPluginsPanel(@NotNull Consumer<? super PluginsGroupComponent> selectionListener) {
    MultiSelectionEventHandler eventHandler = new MultiSelectionEventHandler();
    myMarketplacePanel = new PluginsGroupComponentWithProgress(eventHandler) {
      @Override
      protected @NotNull ListPluginComponent createListComponent(@NotNull PluginUiModel model,
                                                                 @NotNull PluginsGroup group,
                                                                 @NotNull ListPluginModel listPluginModel) {
        return new ListPluginComponent(myPluginModelFacade,
                                       model,
                                       group,
                                       listPluginModel,
                                       searchListener,
                                       myCoroutineScope,
                                       true);
      }
    };

    myMarketplacePanel.setSelectionListener(selectionListener);
    myMarketplacePanel.getAccessibleContext().setAccessibleName(IdeBundle.message("plugin.manager.marketplace.panel.accessible.name"));
    registerCopyProvider(myMarketplacePanel);

    //noinspection ConstantConditions
    ((SearchUpDownPopupController)myMarketplaceSearchPanel.controller).setEventHandler(eventHandler);

    Project project = ProjectUtil.getActiveProject();

    myMarketplaceRunnable = () -> {
      myMarketplacePanel.clear();
      myMarketplacePanel.showLoadingIcon();
      doCreateMarketplaceTab(selectionListener, project);
    };

    myMarketplacePanel.getEmptyText().setText(IdeBundle.message("plugins.configurable.marketplace.plugins.not.loaded"))
      .appendSecondaryText(IdeBundle.message("message.check.the.internet.connection.and") + " ", StatusText.DEFAULT_ATTRIBUTES, null)
      .appendSecondaryText(IdeBundle.message("message.link.refresh"), SimpleTextAttributes.LINK_PLAIN_ATTRIBUTES,
                           e -> myMarketplaceRunnable.run());

    doCreateMarketplaceTab(selectionListener, project);
    return createScrollPane(myMarketplacePanel, false);
  }

  private void doCreateMarketplaceTab(@NotNull Consumer<? super PluginsGroupComponent> selectionListener, Project project) {
    PluginManagerPanelFactory.INSTANCE.createMarketplacePanel(myCoroutineScope, myPluginModelFacade.getModel(), project, model -> {
      List<PluginsGroup> groups = new ArrayList<>();
      try {
        try {
          if (project != null) {
            addSuggestedGroup(groups, model.getErrors(), model.getSuggestedPlugins(), model.getInstalledPlugins(),
                              model.getInstallationStates());
          }
          PluginsViewCustomizer.PluginsGroupDescriptor internalPluginsGroupDescriptor = model.getInternalPluginsGroupDescriptor();
          if (internalPluginsGroupDescriptor != null) {
            List<PluginUiModel> customPlugins =
              ContainerUtil.map(internalPluginsGroupDescriptor.getPlugins(), it -> new PluginUiModelAdapter(it));
            addGroup(
              groups,
              internalPluginsGroupDescriptor.getName(),
              PluginsGroupType.INTERNAL,
              SearchWords.INTERNAL.getValue(),
              customPlugins,
              group -> customPlugins.size() >= ITEMS_PER_GROUP,
              model.getErrors(),
              model.getInstalledPlugins(),
              model.getInstallationStates()
            );
          }

          Map<String, PluginSearchResult> marketplaceData = model.getMarketplaceData();
          addGroupViaLightDescriptor(
            groups,
            IdeBundle.message("plugins.configurable.staff.picks"),
            PluginsGroupType.STAFF_PICKS,
            "is_featured_search=true",
            SearchWords.STAFF_PICKS.getValue(),
            marketplaceData,
            model.getErrors(),
            model.getInstalledPlugins(),
            model.getInstallationStates()
          );
          addGroupViaLightDescriptor(
            groups,
            IdeBundle.message("plugins.configurable.new.and.updated"),
            PluginsGroupType.NEW_AND_UPDATED,
            "orderBy=update+date",
            "/sortBy:updated",
            marketplaceData,
            model.getErrors(),
            model.getInstalledPlugins(),
            model.getInstallationStates());
          addGroupViaLightDescriptor(
            groups,
            IdeBundle.message("plugins.configurable.top.downloads"),
            PluginsGroupType.TOP_DOWNLOADS,
            "orderBy=downloads",
            "/sortBy:downloads",
            marketplaceData,
            model.getErrors(),
            model.getInstalledPlugins(),
            model.getInstallationStates());
          addGroupViaLightDescriptor(
            groups,
            IdeBundle.message("plugins.configurable.top.rated"),
            PluginsGroupType.TOP_RATED,
            "orderBy=rating",
            "/sortBy:rating",
            marketplaceData,
            model.getErrors(),
            model.getInstalledPlugins(),
            model.getInstallationStates());
        }
        catch (IOException e) {
          LOG.info("Main plugin repository is not available ('" + e.getMessage() + "'). Please check your network settings.");
        }

        for (String host : RepositoryHelper.getCustomPluginRepositoryHosts()) {
          List<PluginUiModel> allDescriptors = model.getCustomRepositories().get(host);
          if (allDescriptors != null) {
            String groupName = IdeBundle.message("plugins.configurable.repository.0", host);
            LOG.info("Marketplace tab: '" + groupName + "' group load started");
            addGroup(groups,
                     groupName,
                     PluginsGroupType.CUSTOM_REPOSITORY,
                     "/repository:\"" + host + "\"",
                     allDescriptors,
                     group -> {
                       PluginsGroup.sortByName(group.getModels());
                       return allDescriptors.size() > ITEMS_PER_GROUP;
                     },
                     model.getErrors(),
                     model.getInstalledPlugins(),
                     model.getInstallationStates());
          }
        }
        if (myPluginManagerCustomizer != null) {
          myPluginManagerCustomizer.ensurePluginStatesLoaded();
        }
      }
      finally {
        ApplicationManager.getApplication().invokeLater(() -> {
          myMarketplacePanel.hideLoadingIcon();
          try {
            PluginLogo.startBatchMode();

            for (PluginsGroup group : groups) {
              myMarketplacePanel.addGroup(group);
            }
          }
          finally {
            PluginLogo.endBatchMode();
          }
          myMarketplacePanel.doLayout();
          myMarketplacePanel.initialSelection();

          PluginUpdateListener.calculateUpdates(myCoroutineScope, updates -> {
            List<PluginUiModel> updateModels = updates == null ? null : new ArrayList<>(updates);
            if (ContainerUtil.isEmpty(updateModels)) {
              clearUpdates(myMarketplacePanel);
              clearUpdates(myMarketplaceSearchPanel.getPanel());
            }
            else {
              applyUpdates(myMarketplacePanel, updateModels);
              applyUpdates(myMarketplaceSearchPanel.getPanel(), updateModels);
            }
            selectionListener.accept(myMarketplacePanel);
            selectionListener.accept(myMarketplaceSearchPanel.getPanel());
          });
        }, ModalityState.any());
      }

      return null;
    });
  }

  @Override
  protected void updateMainSelection(@NotNull Consumer<? super PluginsGroupComponent> selectionListener) {
    selectionListener.accept(myMarketplacePanel);
  }

  @Override
  protected @NotNull SearchResultPanel createSearchPanel(@NotNull Consumer<? super PluginsGroupComponent> selectionListener) {
    SearchUpDownPopupController marketplaceController = new SearchUpDownPopupController(searchTextField) {
      @Override
      protected @NotNull List<String> getAttributes() {
        List<String> attributes = new ArrayList<>();
        attributes.add(SearchWords.TAG.getValue());
        attributes.add(SearchWords.SORT_BY.getValue());
        attributes.add(SearchWords.VENDOR.getValue());
        if (!RepositoryHelper.getCustomPluginRepositoryHosts().isEmpty()) {
          attributes.add(SearchWords.REPOSITORY.getValue());
        }
        attributes.add(SearchWords.STAFF_PICKS.getValue());
        attributes.add(SearchWords.SUGGESTED.getValue());
        if (PluginsViewCustomizerKt.getPluginsViewCustomizer() != NoOpPluginsViewCustomizer.INSTANCE) {
          attributes.add(SearchWords.INTERNAL.getValue());
        }
        return attributes;
      }

      @Override
      protected @Nullable List<String> getValues(@NotNull String attribute) {
        SearchWords word = SearchWords.find(attribute);
        return switch (word) {
          case TAG -> {
            yield getOrCalculateTags();
          }
          case SORT_BY -> ContainerUtil.map(
            Arrays.asList(MarketplaceTabSearchSortByOptions.DOWNLOADS, MarketplaceTabSearchSortByOptions.NAME, MarketplaceTabSearchSortByOptions.RATING, MarketplaceTabSearchSortByOptions.UPDATE_DATE),
            sort -> sort.getQuery()
          );
          case VENDOR -> {
            yield getOrCalculateVendors();
          }
          case REPOSITORY -> RepositoryHelper.getCustomPluginRepositoryHosts();
          case INTERNAL, SUGGESTED, STAFF_PICKS -> null;
          case null -> null;
        };
      }

      @Override
      protected void showPopupForQuery() {
        showSearchPanel(searchTextField.getText());
      }

      @Override
      protected void handleEnter() {
        if (!searchTextField.getText().isEmpty()) {
          handleTrigger("marketplace.suggest.popup.enter");
        }
      }

      @Override
      protected void handlePopupListFirstSelection() {
        handleTrigger("marketplace.suggest.popup.select");
      }

      private void handleTrigger(@NonNls String key) {
        if (myPopup != null && myPopup.type == SearchPopup.Type.SearchQuery) {
          FeatureUsageTracker.getInstance().triggerFeatureUsed(key);
        }
      }
    };

    MultiSelectionEventHandler eventHandler = new MultiSelectionEventHandler();
    marketplaceController.setSearchResultEventHandler(eventHandler);

    PluginsGroupComponentWithProgress panel = new PluginsGroupComponentWithProgress(eventHandler) {
      @Override
      protected @NotNull ListPluginComponent createListComponent(@NotNull PluginUiModel model,
                                                                 @NotNull PluginsGroup group,
                                                                 @NotNull ListPluginModel listPluginModel) {
        return new ListPluginComponent(myPluginModelFacade,
                                       model,
                                       group,
                                       listPluginModel,
                                       searchListener,
                                       myCoroutineScope,
                                       true);
      }
    };

    panel.setSelectionListener(selectionListener);
    registerCopyProvider(panel);

    Project project = ProjectUtil.getActiveProject();

    myMarketplaceSearchPanel = new MarketplacePluginsTabSearchResultPanel(myCoroutineScope, marketplaceController, panel, project, selectionListener,
                                                                          myMarketplaceSortByGroup, () -> myMarketplacePanel);
    return myMarketplaceSearchPanel;
  }

  private List<String> getOrCalculateVendors() {
    if (myVendorsSorted == null ||
        myVendorsSorted.isEmpty() // FIXME seems like it shouldn't be here...
    ) {
      LinkedHashSet<String> vendors = new LinkedHashSet<>();
      try {
        ProcessIOExecutorService.INSTANCE.submit(() -> {
          vendors.addAll(UiPluginManager.getInstance().getAllVendors());
        }).get();
      }
      catch (InterruptedException | ExecutionException e) {
        LOG.error("Error while getting vendors from marketplace", e);
      }
      myVendorsSorted = new ArrayList<>(vendors);
    }
    return myVendorsSorted;
  }

  private List<String> getOrCalculateTags() {
    if (myTagsSorted == null ||
        myTagsSorted.isEmpty() // FIXME seems like it shouldn't be here...
    ) {
      Set<String> allTags = new HashSet<>();
      Set<String> customRepoTags = UiPluginManager.getInstance().getCustomRepoTags();
      if (!customRepoTags.isEmpty()) {
        allTags.addAll(customRepoTags);
      }
      try {
        ProcessIOExecutorService.INSTANCE.submit(() -> {
          allTags.addAll(UiPluginManager.getInstance().getAllPluginsTags());
        }).get();
      }
      catch (InterruptedException | ExecutionException e) {
        LOG.error("Error while getting tags from marketplace", e);
      }
      myTagsSorted = ContainerUtil.sorted(allTags, String::compareToIgnoreCase);
    }
    return myTagsSorted;
  }

  private void handleSortByOptionSelection(MarketplaceSortByAction updateAction) {
    MarketplaceSortByAction removeAction = null;
    MarketplaceSortByAction addAction = null;

    if (updateAction.myState) {
      for (AnAction action : myMarketplaceSortByGroup.getChildren(ActionManager.getInstance())) {
        MarketplaceSortByAction sortByAction = (MarketplaceSortByAction)action;
        if (sortByAction != updateAction && sortByAction.myState) {
          sortByAction.myState = false;
          removeAction = sortByAction;
          break;
        }
      }
      addAction = updateAction;
    }
    else {
      if (updateAction.myOption == MarketplaceTabSearchSortByOptions.RELEVANCE) {
        updateAction.myState = true;
        return;
      }

      for (AnAction action : myMarketplaceSortByGroup.getChildren(ActionManager.getInstance())) {
        MarketplaceSortByAction sortByAction = (MarketplaceSortByAction)action;
        if (sortByAction.myOption == MarketplaceTabSearchSortByOptions.RELEVANCE) {
          sortByAction.myState = true;
          break;
        }
      }

      removeAction = updateAction;
    }

    List<String> queries = new ArrayList<>();
    new SearchQueryParser.Marketplace(searchTextField.getText()) { // FIXME: it's unused - why hasn't it been removed?
      @Override
      protected void addToSearchQuery(@NotNull String query) {
        queries.add(query);
      }

      @Override
      protected void handleAttribute(@NotNull String name, @NotNull String value) {
        queries.add(name + SearchQueryParser.wrapAttribute(value));
      }
    };
    if (removeAction != null) {
      String query = removeAction.getQuery();
      if (query != null) {
        queries.remove(query);
      }
    }
    if (addAction != null) {
      String query = addAction.getQuery();
      if (query != null) {
        queries.add(query);
      }
    }

    String query = StringUtil.join(queries, " ");
    searchTextField.setTextIgnoreEvents(query);
    if (query.isEmpty()) {
      hideSearchPanel();
    }
    else {
      showSearchPanel(query);
    }
  }

  @Override
  protected void onSearchReset() {
    PluginManagerUsageCollector.INSTANCE.searchReset();
  }

  private void addSuggestedGroup(@NotNull List<? super PluginsGroup> groups,
                                 @NotNull Map<@NotNull PluginId,
                                   @NotNull List<@NotNull HtmlChunk>> errors,
                                 @NotNull List<@NotNull PluginUiModel> plugins,
                                 @NotNull Map<@NotNull PluginId, @NotNull PluginUiModel> installedPlugins,
                                 @NotNull Map<@NotNull PluginId, @NotNull PluginInstallationState> installationStates) {
    String groupName = IdeBundle.message("plugins.configurable.suggested");
    LOG.info("Marketplace tab: '" + groupName + "' group load started");

    for (PluginUiModel plugin : plugins) {
      if (plugin.isFromMarketplace()) {
        plugin.setInstallSource(FUSEventSource.PLUGINS_SUGGESTED_GROUP);
      }

      FUSEventSource.PLUGINS_SUGGESTED_GROUP.logPluginSuggested(plugin.getPluginId());
    }
    addGroup(groups,
             groupName,
             PluginsGroupType.SUGGESTED,
             "",
             plugins,
             group -> false,
             errors,
             installedPlugins,
             installationStates);
  }

  private void addGroup(@NotNull List<? super PluginsGroup> groups,
                        @NotNull @Nls String name,
                        @NotNull PluginsGroupType type,
                        @NotNull String showAllQuery,
                        @NotNull List<PluginUiModel> customPlugins,
                        @NotNull Predicate<? super PluginsGroup> showAllPredicate,
                        @NotNull Map<PluginId, List<HtmlChunk>> errors,
                        @NotNull Map<PluginId, PluginUiModel> installedPlugins,
                        @NotNull Map<PluginId, PluginInstallationState> installationStates) {
    PluginsGroup group = new PluginsGroup(name, type);
    group.getPreloadedModel().setErrors(errors);
    group.getPreloadedModel().setInstalledPlugins(installedPlugins);
    group.getPreloadedModel().setPluginInstallationStates(installationStates);
    int i = 0;
    for (Iterator<PluginUiModel> iterator = customPlugins.iterator(); iterator.hasNext() && i < ITEMS_PER_GROUP; i++) {
      group.addModel(iterator.next());
    }

    if (showAllPredicate.test(group)) {
      group.mainAction = new PluginManagerConfigurablePanel.LinkLabelButton<>(IdeBundle.message("plugins.configurable.show.all"),
                                                                              null,
                                                                              searchListener,
                                                                              showAllQuery);
      group.mainAction.setBorder(JBUI.Borders.emptyRight(5));
    }

    if (!group.getModels().isEmpty()) {
      groups.add(group);
    }
    LOG.info("Marketplace tab: '" + name + "' group load finished");
  }

  private void addGroupViaLightDescriptor(@NotNull List<? super PluginsGroup> groups,
                                          @NotNull @Nls String name,
                                          @NotNull PluginsGroupType type,
                                          @NotNull @NonNls String query,
                                          @NotNull @NonNls String showAllQuery,
                                          @NotNull Map<String, PluginSearchResult> marketplaceData,
                                          @NotNull Map<PluginId, List<HtmlChunk>> errors,
                                          @NotNull Map<PluginId, PluginUiModel> installedPluginIds,
                                          @NotNull Map<PluginId, PluginInstallationState> installationStates)
    throws IOException {
    LOG.info("Marketplace tab: '" + name + "' group load started");
    PluginSearchResult searchResult = marketplaceData.get(query);
    if (searchResult.getError() != null) {
      throw new IOException(searchResult.getError());
    }

    List<PluginUiModel> plugins = searchResult.getPlugins();
    for (PluginUiModel plugin : plugins) {
      plugin.setInstallSource(FUSEventSource.PLUGINS_STAFF_PICKS_GROUP);
      FUSEventSource.PLUGINS_STAFF_PICKS_GROUP.logPluginSuggested(plugin.getPluginId());
    }

    addGroup(groups,
             name,
             type,
             showAllQuery,
             plugins,
             __ -> plugins.size() >= ITEMS_PER_GROUP,
             errors,
             installedPluginIds,
             installationStates);
  }

  @Override
  public void dispose() {
    if (myMarketplacePanel != null) {
      myMarketplacePanel.dispose();
    }
    if (myMarketplaceSearchPanel != null) {
      myMarketplaceSearchPanel.dispose();
    }
    super.dispose();
  }

  void onPanelReset(boolean isMarketplaceTabSelected) {
    if (myMarketplacePanel != null) {
      if (isMarketplaceTabSelected) {
        myMarketplaceRunnable.run();
      }
      else {
        myMarketplacePanel.setOnBecomingVisibleCallback(myMarketplaceRunnable);
      }
    }
  }

  @ApiStatus.Internal
  final class MarketplaceSortByAction extends ToggleAction implements DumbAware {
    final MarketplaceTabSearchSortByOptions myOption;
    boolean myState;
    private boolean myVisible;

    private MarketplaceSortByAction(@NotNull MarketplaceTabSearchSortByOptions option) {
      super(option.getPresentableNameSupplier());
      getTemplatePresentation().setKeepPopupOnPerform(KeepPopupOnPerform.IfRequested);
      myOption = option;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      super.update(e);
      e.getPresentation().setVisible(myVisible);
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.BGT;
    }

    @Override
    public boolean isSelected(@NotNull AnActionEvent e) {
      return myState;
    }

    @Override
    public void setSelected(@NotNull AnActionEvent e, boolean state) {
      myState = state;
      handleSortByOptionSelection(this);
    }

    public void setState(@NotNull SearchQueryParser.Marketplace parser) {
      if (myOption == MarketplaceTabSearchSortByOptions.RELEVANCE) {
        myState = parser.sortBy == null;
        myVisible = parser.sortBy == null || !parser.tags.isEmpty() || !parser.vendors.isEmpty() || parser.searchQuery != null;
      }
      else {
        myState = parser.sortBy != null && myOption == parser.sortBy;
        myVisible = true;
      }
    }

    public @Nullable String getQuery() {
      if (myOption == MarketplaceTabSearchSortByOptions.RELEVANCE) return null;
      return SearchWords.SORT_BY.getValue() + myOption.getQuery();
    }
  }
}
