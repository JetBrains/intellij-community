// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins;

import com.intellij.execution.process.ProcessIOExecutorService;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.impl.ProjectUtil;
import com.intellij.ide.plugins.marketplace.PluginSearchResult;
import com.intellij.ide.plugins.marketplace.ranking.MarketplaceLocalRanker;
import com.intellij.ide.plugins.marketplace.statistics.PluginManagerUsageCollector;
import com.intellij.ide.plugins.newui.*;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.FUSEventSource;
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.PluginsAdvertiserStartupActivityKt;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.components.labels.LinkListener;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.StatusText;
import kotlinx.coroutines.CoroutineScope;
import org.jetbrains.annotations.*;
import org.jspecify.annotations.NonNull;

import javax.accessibility.AccessibleContext;
import javax.accessibility.AccessibleRole;
import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.util.*;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static com.intellij.ide.plugins.PluginManagerConfigurablePanel.*;

@ApiStatus.Internal
class MarketplacePluginsTab extends PluginsTab {
  private static final Logger LOG = Logger.getInstance(MarketplacePluginsTab.class);

  private static final int ITEMS_PER_GROUP = 9;

  private final @NotNull PluginModelFacade myPluginModelFacade;
  private final @NotNull CoroutineScope myCoroutineScope;
  private final @Nullable PluginManagerCustomizer myPluginManagerCustomizer;
  private final @NotNull PluginUpdatesService myPluginUpdatesService;

  private PluginsGroupComponentWithProgress myMarketplacePanel;
  private SearchResultPanel myMarketplaceSearchPanel;
  private Runnable myMarketplaceRunnable;

  private final DefaultActionGroup myMarketplaceSortByGroup;
  private LinkComponent myMarketplaceSortByAction;

  private List<String> myTagsSorted;
  private List<String> myVendorsSorted;

  MarketplacePluginsTab(
    @NotNull PluginModelFacade facade,
    @NotNull CoroutineScope scope,
    @Nullable PluginManagerCustomizer customizer,
    @NotNull PluginUpdatesService service
  ) {
    super();
    myPluginModelFacade = facade;
    myCoroutineScope = scope;
    myPluginManagerCustomizer = customizer;
    myPluginUpdatesService = service;

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
      myMarketplacePanel.startLoading();
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
          myMarketplacePanel.stopLoading();
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

          myPluginUpdatesService.calculateUpdates(updates -> {
            if (ContainerUtil.isEmpty(updates)) {
              clearUpdates(myMarketplacePanel);
              clearUpdates(myMarketplaceSearchPanel.getPanel());
            }
            else {
              applyUpdates(myMarketplacePanel, updates);
              applyUpdates(myMarketplaceSearchPanel.getPanel(), updates);
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

    myMarketplaceSortByAction = new LinkComponent() {
      @Override
      protected boolean isInClickableArea(Point pt) {
        return true;
      }

      @Override
      public AccessibleContext getAccessibleContext() {
        if (accessibleContext == null) {
          accessibleContext = new AccessibleLinkComponent();
        }
        return accessibleContext;
      }

      protected class AccessibleLinkComponent extends AccessibleLinkLabel {
        @Override
        public AccessibleRole getAccessibleRole() {
          return AccessibleRole.COMBO_BOX;
        }
      }
    };
    myMarketplaceSortByAction.setIcon(new Icon() {
      @Override
      public void paintIcon(Component c, Graphics g, int x, int y) {
        getIcon().paintIcon(c, g, x, y + 1);
      }

      @Override
      public int getIconWidth() {
        return getIcon().getIconWidth();
      }

      @Override
      public int getIconHeight() {
        return getIcon().getIconHeight();
      }

      private static @NotNull Icon getIcon() {
        return AllIcons.General.ButtonDropTriangle;
      }
    }); // TODO: icon
    myMarketplaceSortByAction.setPaintUnderline(false);
    myMarketplaceSortByAction.setIconTextGap(JBUIScale.scale(4));
    myMarketplaceSortByAction.setHorizontalTextPosition(SwingConstants.LEFT);
    myMarketplaceSortByAction.setForeground(PluginsGroupComponent.SECTION_HEADER_FOREGROUND);

    //noinspection unchecked
    myMarketplaceSortByAction.setListener(
      (component, __) -> showRightBottomPopup(component.getParent().getParent(), IdeBundle.message("plugins.configurable.sort.by"),
                                              myMarketplaceSortByGroup), null);

    DumbAwareAction.create(event -> myMarketplaceSortByAction.doClick())
      .registerCustomShortcutSet(KeyEvent.VK_DOWN, 0, myMarketplaceSortByAction);

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

    myMarketplaceSearchPanel =
      new SearchResultPanel(marketplaceController, panel, true, 0, 0) {
        @Override
        @SuppressWarnings("unchecked")
        protected void handleQuery(@NotNull String query, @NotNull PluginsGroup result, AtomicBoolean runQuery) {
          int searchIndex = PluginManagerUsageCollector.updateAndGetSearchIndex();

          SearchQueryParser.Marketplace parser = new SearchQueryParser.Marketplace(query);

          Map<PluginUiModel, Double> pluginToScore = null;

          if (parser.internal) {
            try {
              PluginsViewCustomizer.PluginsGroupDescriptor groupDescriptor =
                PluginsViewCustomizerKt.getPluginsViewCustomizer().getInternalPluginsGroupDescriptor();
              if (groupDescriptor != null) {
                if (parser.searchQuery == null) {
                  result.addDescriptors(groupDescriptor.getPlugins());
                }
                else {
                  for (IdeaPluginDescriptor pluginDescriptor : groupDescriptor.getPlugins()) {
                    if (StringUtil.containsIgnoreCase(pluginDescriptor.getName(), parser.searchQuery)) {
                      result.addDescriptor(pluginDescriptor);
                    }
                  }
                }
                result.removeDuplicates();
                result.sortByName();
                return;
              }
            }
            catch (Exception e) {
              LOG.error("Error while loading internal plugins group", e);
            }
          }

          PluginModelAsyncOperationsExecutor.INSTANCE.getCustomRepositoriesPluginMap(myCoroutineScope, map -> {
            Map<String, List<PluginUiModel>> customRepositoriesMap = (Map<String, List<PluginUiModel>>)map;
            if (parser.suggested && project != null) {
              List<@NotNull PluginUiModel> plugins =
                PluginsAdvertiserStartupActivityKt.findSuggestedPlugins(project, customRepositoriesMap);
              result.addModels(plugins);
              updateSearchPanel(result, runQuery, plugins);
            }
            else if (!parser.repositories.isEmpty()) {
              for (String repository : parser.repositories) {
                List<PluginUiModel> descriptors = customRepositoriesMap.get(repository);
                if (descriptors == null) {
                  continue;
                }
                if (parser.searchQuery == null) {
                  result.addModels(descriptors);
                }
                else {
                  for (PluginUiModel descriptor : descriptors) {
                    if (StringUtil.containsIgnoreCase(descriptor.getName(), parser.searchQuery)) {
                      result.addModel(descriptor);
                    }
                  }
                }
              }
              result.removeDuplicates();
              result.sortByName();
              updateSearchPanel(result, runQuery, result.getModels());
            }
            else {
              PluginModelAsyncOperationsExecutor.INSTANCE
                .performMarketplaceSearch(myCoroutineScope,
                                          parser.getUrlQuery(),
                                          !result.getModels().isEmpty(),
                                          (searchResult, updates) -> {
                                            applySearchResult(result, searchResult, (List<PluginUiModel>)updates, customRepositoriesMap,
                                                              parser, pluginToScore, searchIndex);
                                            updatePanel(runQuery);
                                            return null;
                                          });
            }
            return null;
          });
        }

        private void updateSearchPanel(@NonNull PluginsGroup result, AtomicBoolean runQuery, List<@NotNull PluginUiModel> plugins) {
          Set<PluginId> ids = plugins.stream().map(it -> it.getPluginId()).collect(Collectors.toSet());
          result.getPreloadedModel().setInstalledPlugins(UiPluginManager.getInstance().findInstalledPluginsSync(ids));
          result.getPreloadedModel().setPluginInstallationStates(UiPluginManager.getInstance().getInstallationStatesSync());
          updatePanel(runQuery);
        }

        private void applySearchResult(@NotNull PluginsGroup result,
                                       PluginSearchResult searchResult,
                                       List<PluginUiModel> updates,
                                       Map<String, List<PluginUiModel>> customRepositoriesMap,
                                       SearchQueryParser.Marketplace parser,
                                       Map<PluginUiModel, Double> pluginToScore,
                                       int searchIndex) {
          if (searchResult.getError() != null) {
            ApplicationManager.getApplication().invokeLater(
              () -> myPanel.getEmptyText()
                .setText(IdeBundle.message("plugins.configurable.search.result.not.loaded"))
                .appendSecondaryText(
                  IdeBundle.message("plugins.configurable.check.internet"),
                  StatusText.DEFAULT_ATTRIBUTES, null), ModalityState.any()
            );
          }
          // compare plugin versions between marketplace & custom repositories
          List<PluginUiModel> customPlugins = ContainerUtil.flatten(customRepositoriesMap.values());
          Collection<PluginUiModel> plugins =
            RepositoryHelper.mergePluginModelsFromRepositories(searchResult.getPlugins(),
                                                               customPlugins,
                                                               false);
          result.addModels(0, new ArrayList<>(plugins));

          if (parser.searchQuery != null) {
            List<PluginUiModel> descriptors = ContainerUtil.filter(customPlugins,
                                                                   descriptor -> StringUtil.containsIgnoreCase(
                                                                     descriptor.getName(),
                                                                     parser.searchQuery));
            result.addModels(0, descriptors);
          }

          result.removeDuplicates();

          final var localRanker = MarketplaceLocalRanker.getInstanceIfEnabled();
          if (localRanker != null) {
            pluginToScore = localRanker.rankPlugins(parser, result.getModels());
          }

          if (!result.getModels().isEmpty()) {
            String title = IdeBundle.message("plugin.manager.action.label.sort.by.1");

            for (AnAction action : myMarketplaceSortByGroup.getChildren(ActionManager.getInstance())) {
              MarketplaceSortByAction sortByAction = (MarketplaceSortByAction)action;
              sortByAction.setState(parser);
              if (sortByAction.myState) {
                title = IdeBundle.message("plugin.manager.action.label.sort.by",
                                          sortByAction.myOption.getPresentableNameSupplier().get());
              }
            }

            myMarketplaceSortByAction.setText(title);
            result.addRightAction(myMarketplaceSortByAction);

            if (!ContainerUtil.isEmpty(updates)) {
              myPostFillGroupCallback = () -> {
                applyUpdates(myPanel, updates);
                selectionListener.accept(myMarketplacePanel);
                selectionListener.accept(myMarketplaceSearchPanel.getPanel());
              };
            }
          }
          Set<PluginId> ids = result.getModels().stream().map(it -> it.getPluginId()).collect(Collectors.toSet());
          result.getPreloadedModel().setInstalledPlugins(UiPluginManager.getInstance().findInstalledPluginsSync(ids));
          result.getPreloadedModel().setPluginInstallationStates(UiPluginManager.getInstance().getInstallationStatesSync());
          PluginManagerUsageCollector.INSTANCE.performMarketplaceSearch(ProjectUtil.getActiveProject(), parser, result.getModels(),
                                                                        searchIndex, pluginToScore);
        }
      };
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
      group.rightAction = new PluginManagerConfigurablePanel.LinkLabelButton<>(IdeBundle.message("plugins.configurable.show.all"),
                                                                               null,
                                                                               searchListener,
                                                                               showAllQuery);
      group.rightAction.setBorder(JBUI.Borders.emptyRight(5));
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

  private final class MarketplaceSortByAction extends ToggleAction implements DumbAware {
    private final MarketplaceTabSearchSortByOptions myOption;
    private boolean myState;
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