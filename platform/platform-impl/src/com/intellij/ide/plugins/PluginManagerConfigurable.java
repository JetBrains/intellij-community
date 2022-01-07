// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins;

import com.intellij.application.options.RegistryManager;
import com.intellij.execution.process.ProcessIOExecutorService;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.icons.AllIcons;
import com.intellij.ide.CopyProvider;
import com.intellij.ide.DataManager;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.plugins.certificates.PluginCertificateManager;
import com.intellij.ide.plugins.enums.PluginsGroupType;
import com.intellij.ide.plugins.marketplace.MarketplaceRequests;
import com.intellij.ide.plugins.newui.*;
import com.intellij.ide.plugins.org.PluginManagerFilters;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.application.impl.ApplicationInfoImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupListener;
import com.intellij.openapi.ui.popup.LightweightWindowEvent;
import com.intellij.openapi.updateSettings.impl.UpdateChecker;
import com.intellij.openapi.updateSettings.impl.UpdateSettings;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.JBColor;
import com.intellij.ui.LicensingFacade;
import com.intellij.ui.RelativeFont;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextField;
import com.intellij.ui.components.fields.ExtendableTextComponent;
import com.intellij.ui.components.labels.LinkLabel;
import com.intellij.ui.components.labels.LinkListener;
import com.intellij.ui.popup.PopupFactoryImpl;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.net.HttpConfigurable;
import com.intellij.util.ui.*;
import org.jetbrains.annotations.*;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public final class PluginManagerConfigurable
  implements SearchableConfigurable, Configurable.NoScroll, Configurable.NoMargin, Configurable.TopComponentProvider {

  private static final Logger LOG = Logger.getInstance(PluginManagerConfigurable.class);

  public static final String ID = "preferences.pluginManager";
  public static final String SELECTION_TAB_KEY = "PluginConfigurable.selectionTab";

  @SuppressWarnings("UseJBColor") public static final Color MAIN_BG_COLOR =
    JBColor.namedColor("Plugins.background", JBColor.lazy(() -> JBColor.isBright() ? UIUtil.getListBackground() : new Color(0x313335)));
  public static final Color SEARCH_BG_COLOR = JBColor.namedColor("Plugins.SearchField.background", MAIN_BG_COLOR);
  public static final Color SEARCH_FIELD_BORDER_COLOR =
    JBColor.namedColor("Plugins.SearchField.borderColor", new JBColor(0xC5C5C5, 0x515151));

  private static final int MARKETPLACE_TAB = 0;
  private static final int INSTALLED_TAB = 1;

  public static final int ITEMS_PER_GROUP = 9;

  public static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("MMM dd, yyyy");

  private TabbedPaneHeaderComponent myTabHeaderComponent;
  private MultiPanel myCardPanel;

  private PluginsTab myMarketplaceTab;
  private PluginsTab myInstalledTab;

  private PluginsGroupComponentWithProgress myMarketplacePanel;
  private PluginsGroupComponent myInstalledPanel;

  private final PluginsGroup myBundledUpdateGroup =
    new PluginsGroup(IdeBundle.message("plugins.configurable.bundled.updates"), PluginsGroupType.BUNDLED_UPDATE);

  private Runnable myMarketplaceRunnable;

  private SearchResultPanel myMarketplaceSearchPanel;
  private SearchResultPanel myInstalledSearchPanel;

  private final LinkLabel<Object> myUpdateAll = new LinkLabel<>(IdeBundle.message("plugin.manager.update.all"), null);
  private final LinkLabel<Object> myUpdateAllBundled = new LinkLabel<>(IdeBundle.message("plugin.manager.update.all"), null);
  private final JLabel myUpdateCounter = new CountComponent();
  private final JLabel myUpdateCounterBundled = new CountComponent();
  private final CountIcon myCountIcon = new CountIcon();

  private final MyPluginModel myPluginModel;

  private PluginUpdatesService myPluginUpdatesService;

  private List<String> myTagsSorted;
  private List<String> myVendorsSorted;

  private DefaultActionGroup myMarketplaceSortByGroup;
  private Consumer<MarketplaceSortByAction> myMarketplaceSortByCallback;
  private LinkComponent myMarketplaceSortByAction;

  private DefaultActionGroup myInstalledSearchGroup;
  private Consumer<InstalledSearchOptionAction> myInstalledSearchCallback;
  private boolean myInstalledSearchSetState = true;

  private String myLaterSearchQuery;
  private boolean myShowMarketplaceTab;

  public PluginManagerConfigurable(@Nullable Project project) {
    myPluginModel = new MyPluginModel(project);
  }

  public PluginManagerConfigurable() {
    this((Project)null);
  }

  /**
   * @deprecated use {@link PluginManagerConfigurable}
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.3")
  public PluginManagerConfigurable(PluginManagerUISettings uiSettings) {
    this();
  }

  @NotNull
  @Override
  public String getId() {
    return ID;
  }

  @Override
  public String getDisplayName() {
    return IdeBundle.message("title.plugins");
  }

  @Override
  public @NotNull JComponent getCenterComponent(@NotNull TopComponentController controller) {
    myPluginModel.setTopController(controller);
    return myTabHeaderComponent;
  }

  public @NotNull JComponent getTopComponent() {
    return getCenterComponent(TopComponentController.EMPTY);
  }

  @Nullable
  @Override
  public JComponent createComponent() {
    myTabHeaderComponent = new TabbedPaneHeaderComponent(createGearActions(), index -> {
      myCardPanel.select(index, true);
      storeSelectionTab(index);

      String query = (index == MARKETPLACE_TAB ? myInstalledTab : myMarketplaceTab).getSearchQuery();
      (index == MARKETPLACE_TAB ? myMarketplaceTab : myInstalledTab).setSearchQuery(query);
    });

    myUpdateAll.setVisible(false);
    myUpdateAllBundled.setVisible(false);
    myUpdateCounter.setVisible(false);
    myUpdateCounterBundled.setVisible(false);

    myTabHeaderComponent.addTab(IdeBundle.message("plugin.manager.tab.marketplace"), null);
    myTabHeaderComponent.addTab(IdeBundle.message("plugin.manager.tab.installed"), myCountIcon);

    myPluginUpdatesService = PluginUpdatesService.connectWithCounter(countValue -> {
      int count = countValue == null ? 0 : countValue;
      String text = Integer.toString(count);
      boolean visible = count > 0;

      String tooltip = PluginUpdatesService.getUpdatesTooltip();
      myTabHeaderComponent.setTabTooltip(INSTALLED_TAB, tooltip);

      myUpdateAll.setEnabled(true);
      myUpdateAllBundled.setEnabled(true);
      myUpdateAll.setVisible(visible && myBundledUpdateGroup.ui == null);
      myUpdateAllBundled.setVisible(visible);

      myUpdateCounter.setText(text);
      myUpdateCounter.setToolTipText(tooltip);
      myUpdateCounterBundled.setText(text);
      myUpdateCounterBundled.setToolTipText(tooltip);
      myUpdateCounter.setVisible(visible && myBundledUpdateGroup.ui == null);
      myUpdateCounterBundled.setVisible(visible);

      myCountIcon.setText(text);
      myTabHeaderComponent.update();
    });
    myPluginModel.setPluginUpdatesService(myPluginUpdatesService);

    UpdateChecker.updateDescriptorsForInstalledPlugins(InstalledPluginsState.getInstance());

    createMarketplaceTab();
    createInstalledTab();

    myCardPanel = new MultiPanel() {
      @Override
      protected JComponent create(Integer key) {
        if (key == MARKETPLACE_TAB) {
          return myMarketplaceTab.createPanel();
        }
        if (key == INSTALLED_TAB) {
          return myInstalledTab.createPanel();
        }
        return super.create(key);
      }
    };
    myCardPanel.setMinimumSize(new JBDimension(580, 380));
    myCardPanel.setPreferredSize(new JBDimension(800, 600));

    myTabHeaderComponent.setListener();

    int selectionTab = getStoredSelectionTab();
    myTabHeaderComponent.setSelection(selectionTab);
    myCardPanel.select(selectionTab, true);

    if (myLaterSearchQuery != null) {
      Runnable search = enableSearch(myLaterSearchQuery);
      if (search != null) {
        ApplicationManager.getApplication().invokeLater(search, ModalityState.any());
      }
      myLaterSearchQuery = null;
    }

    return myCardPanel;
  }

  @NotNull
  private DefaultActionGroup createGearActions() {
    DefaultActionGroup actions = new DefaultActionGroup();
    actions.add(new DumbAwareAction(IdeBundle.message("plugin.manager.repositories")) {
      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        if (ShowSettingsUtil.getInstance().editConfigurable(myCardPanel, new PluginHostsConfigurable())) {
          resetPanels();
        }
      }
    });
    actions.add(new DumbAwareAction(IdeBundle.message("button.http.proxy.settings")) {
      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        if (HttpConfigurable.editConfigurable(myCardPanel)) {
          resetPanels();
        }
      }
    });
    actions.addSeparator();
    actions.add(new DumbAwareAction(IdeBundle.message("plugin.manager.custom.certificates")) {
      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        if (ShowSettingsUtil.getInstance().editConfigurable(myCardPanel, new PluginCertificateManager())) {
          resetPanels();
        }
      }
    });
    actions.add(new InstallFromDiskAction());
    actions.addSeparator();
    actions.add(new ChangePluginStateAction(false));
    actions.add(new ChangePluginStateAction(true));

    return actions;
  }

  private static void showRightBottomPopup(@NotNull Component component, @NotNull @Nls String title, @NotNull ActionGroup group) {
    DefaultActionGroup actions = new GroupByActionGroup();
    actions.addSeparator("  " + title);
    actions.addAll(group);

    DataContext context = DataManager.getInstance().getDataContext(component);

    JBPopup popup = new PopupFactoryImpl.ActionGroupPopup(null, actions, context, false, false, false, true, null, -1, null, null);
    popup.addListener(new JBPopupListener() {
      @Override
      public void beforeShown(@NotNull LightweightWindowEvent event) {
        Point location = component.getLocationOnScreen();
        Dimension size = popup.getSize();
        popup.setLocation(new Point(location.x + component.getWidth() - size.width, location.y + component.getHeight()));
      }
    });
    popup.show(component);
  }

  private void resetPanels() {
    CustomPluginRepositoryService.getInstance().clearCache();

    myTagsSorted = null;
    myVendorsSorted = null;


    myPluginUpdatesService.recalculateUpdates();

    if (myMarketplacePanel == null) {
      return;
    }

    int selectionTab = myTabHeaderComponent.getSelectionTab();
    if (selectionTab == MARKETPLACE_TAB) {
      myMarketplaceRunnable.run();
    }
    else {
      myMarketplacePanel.setVisibleRunnable(myMarketplaceRunnable);
    }
  }

  private static int getStoredSelectionTab() {
    int value = PropertiesComponent.getInstance().getInt(SELECTION_TAB_KEY, MARKETPLACE_TAB);
    return value < MARKETPLACE_TAB || value > INSTALLED_TAB ? MARKETPLACE_TAB : value;
  }

  private static void storeSelectionTab(int value) {
    PropertiesComponent.getInstance().setValue(SELECTION_TAB_KEY, value, MARKETPLACE_TAB);
  }

  private void createMarketplaceTab() {
    myMarketplaceTab = new PluginsTab() {
      @Override
      protected void createSearchTextField(int flyDelay) {
        super.createSearchTextField(250);
        mySearchTextField.setHistoryPropertyName("MarketplacePluginsSearchHistory");
      }

      @NotNull
      @Override
      protected PluginDetailsPageComponent createDetailsPanel(@NotNull LinkListener<Object> searchListener) {
        PluginDetailsPageComponent detailPanel = new PluginDetailsPageComponent(myPluginModel, searchListener, true);
        myPluginModel.addDetailPanel(detailPanel);
        return detailPanel;
      }

      @NotNull
      @Override
      protected JComponent createPluginsPanel(@NotNull Consumer<? super PluginsGroupComponent> selectionListener) {
        MultiSelectionEventHandler eventHandler = new MultiSelectionEventHandler();
        myMarketplacePanel = new PluginsGroupComponentWithProgress(eventHandler) {
          @Override
          protected @NotNull ListPluginComponent createListComponent(@NotNull IdeaPluginDescriptor descriptor, @NotNull PluginsGroup group) {
            return new ListPluginComponent(myPluginModel, descriptor, group, mySearchListener, true);
          }
        };

        myMarketplacePanel.setSelectionListener(selectionListener);
        registerCopyProvider(myMarketplacePanel);

        //noinspection ConstantConditions
        ((SearchUpDownPopupController)myMarketplaceSearchPanel.controller).setEventHandler(eventHandler);

        Runnable runnable = () -> {
          List<PluginsGroup> groups = new ArrayList<>();

          try {
            Map<String, List<PluginNode>> customRepositoriesMap = CustomPluginRepositoryService.getInstance()
              .getCustomRepositoryPluginMap();
            try {
              addGroupViaLightDescriptor(
                groups,
                IdeBundle.message("plugins.configurable.featured"),
                PluginsGroupType.FEATURED,
                "is_featured_search=true",
                "/sortBy:featured"
              );
              addGroupViaLightDescriptor(
                groups,
                IdeBundle.message("plugins.configurable.new.and.updated"),
                PluginsGroupType.NEW_AND_UPDATED,
                "orderBy=update+date",
                "/sortBy:updated"
              );
              addGroupViaLightDescriptor(
                groups,
                IdeBundle.message("plugins.configurable.top.downloads"),
                PluginsGroupType.TOP_DOWNLOADS,
                "orderBy=downloads",
                "/sortBy:downloads"
              );
              addGroupViaLightDescriptor(
                groups,
                IdeBundle.message("plugins.configurable.top.rated"),
                PluginsGroupType.TOP_RATED,
                "orderBy=rating",
                "/sortBy:rating"
              );
            }
            catch (IOException e) {
              LOG.info("Main plugin repository is not available ('" + e.getMessage() + "'). Please check your network settings.");
            }

            for (String host : UpdateSettings.getInstance().getPluginHosts()) {
              List<PluginNode> allDescriptors = customRepositoriesMap.get(host);
              if (allDescriptors != null) {
                addGroup(groups,
                         IdeBundle.message("plugins.configurable.repository.0", host),
                         PluginsGroupType.CUSTOM_REPOSITORY,
                         "/repository:\"" + host + "\"",
                         allDescriptors,
                         group -> {
                           PluginsGroup.sortByName(group.descriptors);
                           return allDescriptors.size() > ITEMS_PER_GROUP;
                         });
              }
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
            }, ModalityState.any());
          }
        };

        myMarketplaceRunnable = () -> {
          myMarketplacePanel.clear();
          myMarketplacePanel.startLoading();
          ApplicationManager.getApplication().executeOnPooledThread(runnable);
        };

        myMarketplacePanel.getEmptyText().setText(IdeBundle.message("plugins.configurable.marketplace.plugins.not.loaded"))
          .appendSecondaryText(IdeBundle.message("message.check.the.internet.connection.and") + " ", StatusText.DEFAULT_ATTRIBUTES, null)
          .appendSecondaryText(IdeBundle.message("message.link.refresh"), SimpleTextAttributes.LINK_PLAIN_ATTRIBUTES,
                               e -> myMarketplaceRunnable.run());

        ApplicationManager.getApplication().executeOnPooledThread(runnable);
        return createScrollPane(myMarketplacePanel, false);
      }

      @Override
      protected void updateMainSelection(@NotNull Consumer<? super PluginsGroupComponent> selectionListener) {
        selectionListener.accept(myMarketplacePanel);
      }

      @NotNull
      @Override
      protected SearchResultPanel createSearchPanel(@NotNull Consumer<? super PluginsGroupComponent> selectionListener) {
        SearchUpDownPopupController marketplaceController = new SearchUpDownPopupController(mySearchTextField) {
          @NotNull
          @Override
          protected List<String> getAttributes() {
            List<String> attributes = new ArrayList<>();
            attributes.add(SearchWords.TAG.getValue());
            attributes.add(SearchWords.SORT_BY.getValue());
            attributes.add(SearchWords.ORGANIZATION.getValue());
            if (!UpdateSettings.getInstance().getPluginHosts().isEmpty()) {
              attributes.add(SearchWords.REPOSITORY.getValue());
            }
            return attributes;
          }

          @Nullable
          @Override
          protected List<String> getValues(@NotNull String attribute) {
            SearchWords word = SearchWords.find(attribute);
            if (word == null) return null;
            switch (word) {
              case TAG:
                if (myTagsSorted == null || myTagsSorted.isEmpty()) {
                  Set<String> allTags = new HashSet<>();
                  for (PluginNode descriptor : CustomPluginRepositoryService.getInstance().getCustomRepositoryPlugins()) {
                    List<String> tags = descriptor.getTags();
                    if (tags != null && !tags.isEmpty()) {
                      allTags.addAll(tags);
                    }
                  }
                  try {
                    ProcessIOExecutorService.INSTANCE.submit(() -> {
                      allTags.addAll(MarketplaceRequests.getInstance().getAllPluginsTags());
                    }).get();
                  }
                  catch (InterruptedException | ExecutionException e) {
                    LOG.error("Error while getting tags from marketplace", e);
                  }
                  myTagsSorted = ContainerUtil.sorted(allTags, String::compareToIgnoreCase);
                }
                return myTagsSorted;
              case SORT_BY:
                return Arrays.asList("downloads", "name", "rating", "updated");
              case ORGANIZATION:
                if (myVendorsSorted == null || myVendorsSorted.isEmpty()) {
                  LinkedHashSet<String> vendors = new LinkedHashSet<>();
                  try {
                    ProcessIOExecutorService.INSTANCE.submit(() -> {
                      vendors.addAll(MarketplaceRequests.getInstance().getAllPluginsVendors());
                    }).get();
                  }
                  catch (InterruptedException | ExecutionException e) {
                    LOG.error("Error while getting vendors from marketplace", e);
                  }
                  myVendorsSorted = new ArrayList<>(vendors);
                }
                return myVendorsSorted;
              case REPOSITORY:
                return UpdateSettings.getInstance().getPluginHosts();
            }
            return null;
          }

          @Override
          protected void showPopupForQuery() {
            showSearchPanel(mySearchTextField.getText());
          }

          @Override
          protected void handleEnter() {
            if (!mySearchTextField.getText().isEmpty()) {
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

        myMarketplaceSortByGroup = new DefaultActionGroup();

        for (SortBySearchOption option : SortBySearchOption.values()) {
          myMarketplaceSortByGroup.addAction(new MarketplaceSortByAction(option));
        }

        myMarketplaceSortByAction = new LinkComponent() {
          @Override
          protected boolean isInClickableArea(Point pt) {
            return true;
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

          @NotNull
          private Icon getIcon() {
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

        myMarketplaceSortByCallback = updateAction -> {
          MarketplaceSortByAction removeAction = null;
          MarketplaceSortByAction addAction = null;

          if (updateAction.myState) {
            for (AnAction action : myMarketplaceSortByGroup.getChildren(null)) {
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
            if (updateAction.myOption == SortBySearchOption.Relevance) {
              updateAction.myState = true;
              return;
            }

            for (AnAction action : myMarketplaceSortByGroup.getChildren(null)) {
              MarketplaceSortByAction sortByAction = (MarketplaceSortByAction)action;
              if (sortByAction.myOption == SortBySearchOption.Relevance) {
                sortByAction.myState = true;
                break;
              }
            }

            removeAction = updateAction;
          }

          List<String> queries = new ArrayList<>();
          new SearchQueryParser.Marketplace(mySearchTextField.getText()) {
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
          mySearchTextField.setTextIgnoreEvents(query);
          if (query.isEmpty()) {
            myMarketplaceTab.hideSearchPanel();
          }
          else {
            myMarketplaceTab.showSearchPanel(query);
          }
        };

        MultiSelectionEventHandler eventHandler = new MultiSelectionEventHandler();
        marketplaceController.setSearchResultEventHandler(eventHandler);

        PluginsGroupComponentWithProgress panel = new PluginsGroupComponentWithProgress(eventHandler) {
          @Override
          protected @NotNull ListPluginComponent createListComponent(@NotNull IdeaPluginDescriptor descriptor, @NotNull PluginsGroup group) {
            return new ListPluginComponent(myPluginModel, descriptor, group, mySearchListener, true);
          }
        };

        panel.setSelectionListener(selectionListener);
        registerCopyProvider(panel);

        myMarketplaceSearchPanel =
          new SearchResultPanel(marketplaceController, panel, true, 0, 0) {
            @Override
            protected void handleQuery(@NotNull String query, @NotNull PluginsGroup result) {
              try {
                Map<String, List<PluginNode>> customRepositoriesMap =
                  CustomPluginRepositoryService.getInstance().getCustomRepositoryPluginMap();

                SearchQueryParser.Marketplace parser = new SearchQueryParser.Marketplace(query);

                if (!parser.repositories.isEmpty()) {
                  for (String repository : parser.repositories) {
                    List<PluginNode> descriptors = customRepositoriesMap.get(repository);
                    if (descriptors == null) {
                      continue;
                    }
                    if (parser.searchQuery == null) {
                      result.descriptors.addAll(descriptors);
                    }
                    else {
                      for (PluginNode descriptor : descriptors) {
                        if (StringUtil.containsIgnoreCase(descriptor.getName(), parser.searchQuery)) {
                          result.descriptors.add(descriptor);
                        }
                      }
                    }
                  }
                  ContainerUtil.removeDuplicates(result.descriptors);
                  result.sortByName();
                  return;
                }

                List<PluginNode> pluginsFromMarketplace = MarketplaceRequests.getInstance().searchPlugins(parser.getUrlQuery(), 10000);
                // compare plugin versions between marketplace & custom repositories
                List<PluginNode> customPlugins = ContainerUtil.flatten(customRepositoriesMap.values());
                Collection<PluginNode> plugins = RepositoryHelper.mergePluginsFromRepositories(pluginsFromMarketplace,
                                                                                               customPlugins,
                                                                                               false);
                result.descriptors.addAll(0, plugins);

                if (parser.searchQuery != null) {
                  List<PluginNode> descriptors = ContainerUtil.filter(customPlugins,
                                                                      descriptor -> StringUtil.containsIgnoreCase(descriptor.getName(),
                                                                                                                  parser.searchQuery));
                  result.descriptors.addAll(0, descriptors);
                }

                ContainerUtil.removeDuplicates(result.descriptors);

                if (!result.descriptors.isEmpty()) {
                  String title = IdeBundle.message("plugin.manager.action.label.sort.by.1");

                  for (AnAction action : myMarketplaceSortByGroup.getChildren(null)) {
                    MarketplaceSortByAction sortByAction = (MarketplaceSortByAction)action;
                    sortByAction.setState(parser);
                    if (sortByAction.myState) {
                      title = IdeBundle.message("plugin.manager.action.label.sort.by",
                                                sortByAction.myOption.myPresentableNameSupplier.get());
                    }
                  }

                  myMarketplaceSortByAction.setText(title);
                  result.addRightAction(myMarketplaceSortByAction);
                }
              }
              catch (IOException e) {
                LOG.info(e);
                ApplicationManager.getApplication().invokeLater(
                  () -> myPanel.getEmptyText()
                    .setText(IdeBundle.message("plugins.configurable.search.result.not.loaded"))
                    .appendSecondaryText(
                      IdeBundle.message("plugins.configurable.check.internet"), StatusText.DEFAULT_ATTRIBUTES, null), ModalityState.any()
                );
              }
            }
          };

        return myMarketplaceSearchPanel;
      }
    };
  }

  private void createInstalledTab() {
    myInstalledSearchGroup = new DefaultActionGroup();

    for (InstalledSearchOption option : InstalledSearchOption.values()) {
      myInstalledSearchGroup.add(new InstalledSearchOptionAction(option));
    }

    myInstalledTab = new PluginsTab() {
      @Override
      protected void createSearchTextField(int flyDelay) {
        super.createSearchTextField(flyDelay);

        JBTextField textField = mySearchTextField.getTextEditor();
        textField.putClientProperty("search.extension", ExtendableTextComponent.Extension
          .create(AllIcons.Actions.More, AllIcons.Actions.More, IdeBundle.message("plugins.configurable.search.options"), // TODO: icon
                  () -> showRightBottomPopup(textField, IdeBundle.message("plugins.configurable.show"), myInstalledSearchGroup)));
        textField.putClientProperty("JTextField.variant", null);
        textField.putClientProperty("JTextField.variant", "search");

        mySearchTextField.setHistoryPropertyName("InstalledPluginsSearchHistory");
      }

      @NotNull
      @Override
      protected PluginDetailsPageComponent createDetailsPanel(@NotNull LinkListener<Object> searchListener) {
        PluginDetailsPageComponent detailPanel = new PluginDetailsPageComponent(myPluginModel, searchListener, false);
        myPluginModel.addDetailPanel(detailPanel);
        return detailPanel;
      }

      @NotNull
      @Override
      protected JComponent createPluginsPanel(@NotNull Consumer<? super PluginsGroupComponent> selectionListener) {
        MultiSelectionEventHandler eventHandler = new MultiSelectionEventHandler();
        myInstalledPanel = new PluginsGroupComponent(eventHandler) {
          @Override
          protected @NotNull ListPluginComponent createListComponent(@NotNull IdeaPluginDescriptor descriptor, @NotNull PluginsGroup group) {
            return new ListPluginComponent(myPluginModel, descriptor, group, mySearchListener, false);
          }
        };

        myInstalledPanel.setSelectionListener(selectionListener);
        registerCopyProvider(myInstalledPanel);

        //noinspection ConstantConditions
        ((SearchUpDownPopupController)myInstalledSearchPanel.controller).setEventHandler(eventHandler);

        try {
          PluginLogo.startBatchMode();

          PluginsGroup installing = new PluginsGroup(IdeBundle.message("plugins.configurable.installing"), PluginsGroupType.INSTALLING);
          installing.descriptors.addAll(MyPluginModel.getInstallingPlugins());
          if (!installing.descriptors.isEmpty()) {
            installing.sortByName();
            installing.titleWithCount();
            myInstalledPanel.addGroup(installing);
          }

          PluginsGroup downloaded = new PluginsGroup(IdeBundle.message("plugins.configurable.downloaded"), PluginsGroupType.INSTALLED);
          downloaded.descriptors.addAll(InstalledPluginsState.getInstance().getInstalledPlugins());

          Map<Boolean, List<IdeaPluginDescriptorImpl>> visiblePlugins = PluginManager
            .getVisiblePlugins(RegistryManager.getInstance().is("plugins.show.implementation.details"))
            .collect(Collectors.partitioningBy(IdeaPluginDescriptorImpl::isBundled));

          List<IdeaPluginDescriptorImpl> nonBundledPlugins = visiblePlugins.get(Boolean.FALSE);
          downloaded.descriptors.addAll(nonBundledPlugins);

          LinkListener<Object> updateAllListener = new LinkListener<>() {
            @Override
            public void linkSelected(LinkLabel<Object> aSource, Object aLinkData) {
              myUpdateAll.setEnabled(false);
              myUpdateAllBundled.setEnabled(false);

              for (UIPluginGroup group : getInstalledGroups()) {
                if (group.excluded) {
                  continue;
                }
                for (ListPluginComponent plugin : group.plugins) {
                  plugin.updatePlugin();
                }
              }
            }
          };
          myUpdateAll.setListener(updateAllListener, null);
          downloaded.addRightAction(myUpdateAll);
          downloaded.addRightAction(myUpdateCounter);

          if (!downloaded.descriptors.isEmpty()) {
            downloaded.sortByName();

            long enabledNonBundledCount = nonBundledPlugins.stream()
              .map(IdeaPluginDescriptorImpl::getPluginId)
              .filter(descriptor -> !PluginManagerCore.isDisabled(descriptor))
              .count();
            downloaded.titleWithCount(Math.toIntExact(enabledNonBundledCount));
            myInstalledPanel.addGroup(downloaded);
            myPluginModel.addEnabledGroup(downloaded);
          }

          myPluginModel.setDownloadedGroup(myInstalledPanel, downloaded, installing);

          String defaultCategory = IdeBundle.message("plugins.configurable.other.bundled");
          visiblePlugins.get(Boolean.TRUE)
            .stream()
            .collect(Collectors.groupingBy(descriptor -> StringUtil.defaultIfEmpty(descriptor.getCategory(), defaultCategory)))
            .entrySet()
            .stream()
            .map(entry -> new ComparablePluginsGroup(entry.getKey(), entry.getValue()))
            .sorted((o1, o2) -> defaultCategory.equals(o1.title) ? 1 :
                                defaultCategory.equals(o2.title) ? -1 :
                                o1.compareTo(o2))
            .forEachOrdered(group -> {
              myInstalledPanel.addGroup(group);
              myPluginModel.addEnabledGroup(group);
            });

          myUpdateAllBundled.setListener(updateAllListener, null);
          myBundledUpdateGroup.addRightAction(myUpdateAllBundled);
          myBundledUpdateGroup.addRightAction(myUpdateCounterBundled);

          myPluginUpdatesService.calculateUpdates(updates -> {
            if (ContainerUtil.isEmpty(updates)) {
              clearUpdates(myInstalledPanel);
              clearUpdates(myInstalledSearchPanel.getPanel());
            }
            else {
              applyUpdates(myInstalledPanel, updates);
              applyUpdates(myInstalledSearchPanel.getPanel(), updates);
            }
            applyBundledUpdates(updates);
            selectionListener.accept(myInstalledPanel);
          });
        }
        finally {
          PluginLogo.endBatchMode();
        }

        return createScrollPane(myInstalledPanel, true);
      }

      @Override
      protected void updateMainSelection(@NotNull Consumer<? super PluginsGroupComponent> selectionListener) {
        selectionListener.accept(myInstalledPanel);
      }

      @Override
      public void hideSearchPanel() {
        super.hideSearchPanel();
        if (myInstalledSearchSetState) {
          for (AnAction action : myInstalledSearchGroup.getChildren(null)) {
            ((InstalledSearchOptionAction)action).setState(null);
          }
        }
        myPluginModel.setInvalidFixCallback(null);
      }

      @NotNull
      @Override
      protected SearchResultPanel createSearchPanel(@NotNull Consumer<? super PluginsGroupComponent> selectionListener) {
        SearchUpDownPopupController installedController = new SearchUpDownPopupController(mySearchTextField) {
          @NotNull
          @Override
          @NonNls
          protected List<String> getAttributes() {
            return Arrays
              .asList(
                "/downloaded",
                "/outdated",
                "/enabled",
                "/disabled",
                "/invalid",
                "/bundled",
                SearchWords.ORGANIZATION.getValue(),
                SearchWords.TAG.getValue()
              );
          }

          @Override
          protected @Nullable SortedSet<String> getValues(@NotNull String attribute) {
            return SearchWords.ORGANIZATION.getValue().equals(attribute) ?
                   myPluginModel.getVendors() :
                   SearchWords.TAG.getValue().equals(attribute) ?
                   myPluginModel.getTags() :
                   null;
          }

          @Override
          protected void showPopupForQuery() {
            showSearchPanel(mySearchTextField.getText());
          }
        };

        MultiSelectionEventHandler eventHandler = new MultiSelectionEventHandler();
        installedController.setSearchResultEventHandler(eventHandler);

        PluginsGroupComponent panel = new PluginsGroupComponent(eventHandler) {
          @Override
          protected @NotNull ListPluginComponent createListComponent(@NotNull IdeaPluginDescriptor descriptor, @NotNull PluginsGroup group) {
            return new ListPluginComponent(myPluginModel, descriptor, group, mySearchListener, false);
          }
        };

        panel.setSelectionListener(selectionListener);
        registerCopyProvider(panel);

        myInstalledSearchCallback = updateAction -> {
          List<String> queries = new ArrayList<>();
          new SearchQueryParser.Installed(mySearchTextField.getText()) {
            @Override
            protected void addToSearchQuery(@NotNull String query) {
              queries.add(query);
            }

            @Override
            protected void handleAttribute(@NotNull String name, @NotNull String value) {
              if (!updateAction.myState) {
                queries.add(name + (value.isEmpty() ? "" : SearchQueryParser.wrapAttribute(value)));
              }
            }
          };

          if (updateAction.myState) {
            for (AnAction action : myInstalledSearchGroup.getChildren(null)) {
              if (action != updateAction) {
                ((InstalledSearchOptionAction)action).myState = false;
              }
            }

            queries.add(updateAction.getQuery());
          }
          else {
            queries.remove(updateAction.getQuery());
          }

          try {
            myInstalledSearchSetState = false;

            String query = StringUtil.join(queries, " ");
            mySearchTextField.setTextIgnoreEvents(query);
            if (query.isEmpty()) {
              myInstalledTab.hideSearchPanel();
            }
            else {
              myInstalledTab.showSearchPanel(query);
            }
          }
          finally {
            myInstalledSearchSetState = true;
          }
        };

        myInstalledSearchPanel = new SearchResultPanel(installedController, panel, false, 0, 0) {
          @Override
          protected void setEmptyText(@NotNull String query) {
            myPanel.getEmptyText().setText(IdeBundle.message("plugins.configurable.nothing.found"));
            if (query.contains("/downloaded") || query.contains("/outdated") ||
                query.contains("/enabled") || query.contains("/disabled") ||
                query.contains("/invalid") || query.contains("/bundled")) {
              return;
            }
            myPanel.getEmptyText().appendSecondaryText(IdeBundle.message("plugins.configurable.search.in.marketplace"),
                                                       SimpleTextAttributes.LINK_PLAIN_ATTRIBUTES,
                                                       e -> myTabHeaderComponent.setSelectionWithEvents(MARKETPLACE_TAB));
          }

          @Override
          protected void handleQuery(@NotNull String query, @NotNull PluginsGroup result) {
            myPluginModel.setInvalidFixCallback(null);

            SearchQueryParser.Installed parser = new SearchQueryParser.Installed(query);

            if (myInstalledSearchSetState) {
              for (AnAction action : myInstalledSearchGroup.getChildren(null)) {
                ((InstalledSearchOptionAction)action).setState(parser);
              }
            }

            List<IdeaPluginDescriptor> descriptors = myPluginModel.getInstalledDescriptors();

            if (!parser.vendors.isEmpty()) {
              for (Iterator<IdeaPluginDescriptor> I = descriptors.iterator(); I.hasNext(); ) {
                if (!MyPluginModel.isVendor(I.next(), parser.vendors)) {
                  I.remove();
                }
              }
            }
            if (!parser.tags.isEmpty()) {
              for (Iterator<IdeaPluginDescriptor> I = descriptors.iterator(); I.hasNext(); ) {
                if (!ContainerUtil.intersects(getTags(I.next()), parser.tags)) {
                  I.remove();
                }
              }
            }
            for (Iterator<IdeaPluginDescriptor> I = descriptors.iterator(); I.hasNext(); ) {
              IdeaPluginDescriptor descriptor = I.next();
              if (parser.attributes) {
                if (parser.enabled && (!myPluginModel.isEnabled(descriptor) || myPluginModel.hasErrors(descriptor))) {
                  I.remove();
                  continue;
                }
                if (parser.disabled && (myPluginModel.isEnabled(descriptor) || myPluginModel.hasErrors(descriptor))) {
                  I.remove();
                  continue;
                }
                if (parser.bundled && !descriptor.isBundled()) {
                  I.remove();
                  continue;
                }
                if (parser.downloaded && descriptor.isBundled()) {
                  I.remove();
                  continue;
                }
                if (parser.invalid && !myPluginModel.hasErrors(descriptor)) {
                  I.remove();
                  continue;
                }
                if (parser.needUpdate && !PluginUpdatesService.isNeedUpdate(descriptor)) {
                  I.remove();
                  continue;
                }
              }
              if (parser.searchQuery != null && !containsQuery(descriptor, parser.searchQuery)) {
                I.remove();
              }
            }

            result.descriptors.addAll(descriptors);

            if (!result.descriptors.isEmpty()) {
              if (parser.invalid) {
                myPluginModel.setInvalidFixCallback(() -> {
                  PluginsGroup group = myInstalledSearchPanel.getGroup();
                  if (group.ui == null) {
                    myPluginModel.setInvalidFixCallback(null);
                    return;
                  }

                  PluginsGroupComponent resultPanel = myInstalledSearchPanel.getPanel();

                  for (IdeaPluginDescriptor descriptor : new ArrayList<>(group.descriptors)) {
                    if (!myPluginModel.hasErrors(descriptor)) {
                      resultPanel.removeFromGroup(group, descriptor);
                    }
                  }

                  group.titleWithCount();
                  myInstalledSearchPanel.fullRepaint();

                  if (group.descriptors.isEmpty()) {
                    myPluginModel.setInvalidFixCallback(null);
                    myInstalledSearchPanel.removeGroup();
                  }
                });
              }
              else if (parser.needUpdate) {
                result.rightAction = new LinkLabel<>(IdeBundle.message("plugin.manager.update.all"), null, (__, ___) -> {
                  result.rightAction.setEnabled(false);

                  for (ListPluginComponent plugin : result.ui.plugins) {
                    plugin.updatePlugin();
                  }
                });
              }

              Collection<IdeaPluginDescriptor> updates = PluginUpdatesService.getUpdates();
              if (!ContainerUtil.isEmpty(updates)) {
                myPostFillGroupCallback = () -> {
                  applyUpdates(myPanel, updates);
                  selectionListener.accept(myInstalledPanel);
                };
              }
            }
          }
        };

        return myInstalledSearchPanel;
      }
    };

    myPluginModel.setCancelInstallCallback(descriptor -> {
      if (myInstalledSearchPanel == null) {
        return;
      }

      PluginsGroup group = myInstalledSearchPanel.getGroup();

      if (group.ui != null && group.ui.findComponent(descriptor) != null) {
        myInstalledSearchPanel.getPanel().removeFromGroup(group, descriptor);
        group.titleWithCount();
        myInstalledSearchPanel.fullRepaint();

        if (group.descriptors.isEmpty()) {
          myInstalledSearchPanel.removeGroup();
        }
      }
    });
  }

  private final class ComparablePluginsGroup extends PluginsGroup
    implements Comparable<ComparablePluginsGroup> {

    private boolean myIsEnable = false;

    private ComparablePluginsGroup(@NotNull @NlsSafe String category,
                                   @NotNull List<? extends IdeaPluginDescriptor> descriptors) {
      super(category, PluginsGroupType.INSTALLED);

      this.descriptors.addAll(descriptors);
      sortByName();

      rightAction = new LinkLabel<>("",
                                    null,
                                    (__, ___) -> setEnabledState());

      titleWithEnabled(myPluginModel);
    }

    @Override
    public int compareTo(@NotNull ComparablePluginsGroup other) {
      return StringUtil.compare(title, other.title, true);
    }

    @Override
    public void titleWithCount(int enabled) {
      myIsEnable = enabled == 0;
      String key = myIsEnable ? "plugins.configurable.enable.all" : "plugins.configurable.disable.all";
      rightAction.setText(IdeBundle.message(key));
    }

    private void setEnabledState() {
      if (myIsEnable) {
        myPluginModel.enable(descriptors);
      }
      else {
        myPluginModel.disable(descriptors);
      }
    }
  }

  private static boolean containsQuery(IdeaPluginDescriptor descriptor, String searchQuery) {
    if (StringUtil.containsIgnoreCase(descriptor.getName(), searchQuery)) return true;

    String description = descriptor.getDescription();
    return description != null && StringUtil.containsIgnoreCase(description, searchQuery);
  }

  private static void clearUpdates(@NotNull PluginsGroupComponent panel) {
    for (UIPluginGroup group : panel.getGroups()) {
      for (ListPluginComponent plugin : group.plugins) {
        plugin.setUpdateDescriptor(null);
      }
    }
  }

  private static void applyUpdates(@NotNull PluginsGroupComponent panel, @NotNull Collection<? extends IdeaPluginDescriptor> updates) {
    for (IdeaPluginDescriptor descriptor : updates) {
      for (UIPluginGroup group : panel.getGroups()) {
        ListPluginComponent component = group.findComponent(descriptor);
        if (component != null) {
          component.setUpdateDescriptor(descriptor);
          break;
        }
      }
    }
  }

  private void applyBundledUpdates(@Nullable Collection<? extends IdeaPluginDescriptor> updates) {
    if (ContainerUtil.isEmpty(updates)) {
      if (myBundledUpdateGroup.ui != null) {
        myInstalledPanel.removeGroup(myBundledUpdateGroup);
        myInstalledPanel.doLayout();
      }
    }
    else if (myBundledUpdateGroup.ui == null) {
      for (IdeaPluginDescriptor descriptor : updates) {
        for (UIPluginGroup group : myInstalledPanel.getGroups()) {
          ListPluginComponent component = group.findComponent(descriptor);
          if (component != null && component.getPluginDescriptor().isBundled()) {
            myBundledUpdateGroup.descriptors.add(component.getPluginDescriptor());
            break;
          }
        }
      }
      if (!myBundledUpdateGroup.descriptors.isEmpty()) {
        myInstalledPanel.addGroup(myBundledUpdateGroup, 0);
        myBundledUpdateGroup.ui.excluded = true;

        for (IdeaPluginDescriptor descriptor : updates) {
          ListPluginComponent component = myBundledUpdateGroup.ui.findComponent(descriptor);
          if (component != null) {
            component.setUpdateDescriptor(descriptor);
          }
        }

        myInstalledPanel.doLayout();
      }
    }
    else {
      List<ListPluginComponent> toDelete = new ArrayList<>();

      for (ListPluginComponent plugin : myBundledUpdateGroup.ui.plugins) {
        boolean exist = false;
        for (IdeaPluginDescriptor update : updates) {
          if (plugin.getPluginDescriptor().getPluginId().equals(update.getPluginId())) {
            exist = true;
            break;
          }
        }
        if (!exist) {
          toDelete.add(plugin);
        }
      }

      for (ListPluginComponent component : toDelete) {
        myInstalledPanel.removeFromGroup(myBundledUpdateGroup, component.getPluginDescriptor());
      }

      for (IdeaPluginDescriptor update : updates) {
        ListPluginComponent exist = myBundledUpdateGroup.ui.findComponent(update);
        if (exist != null) {
          continue;
        }
        for (UIPluginGroup group : myInstalledPanel.getGroups()) {
          if (group == myBundledUpdateGroup.ui) {
            continue;
          }
          ListPluginComponent component = group.findComponent(update);
          if (component != null && component.getPluginDescriptor().isBundled()) {
            myInstalledPanel.addToGroup(myBundledUpdateGroup, component.getPluginDescriptor());
            break;
          }
        }
      }

      if (myBundledUpdateGroup.descriptors.isEmpty()) {
        myInstalledPanel.removeGroup(myBundledUpdateGroup);
      }
      else {
        for (IdeaPluginDescriptor descriptor : updates) {
          ListPluginComponent component = myBundledUpdateGroup.ui.findComponent(descriptor);
          if (component != null) {
            component.setUpdateDescriptor(descriptor);
          }
        }
      }

      myInstalledPanel.doLayout();
    }

    myUpdateAll.setVisible(myUpdateAll.isVisible() && myBundledUpdateGroup.ui == null);
    myUpdateCounter.setVisible(myUpdateCounter.isVisible() && myBundledUpdateGroup.ui == null);
  }

  public static void registerCopyProvider(@NotNull PluginsGroupComponent component) {
    CopyProvider copyProvider = new CopyProvider() {
      @Override
      public void performCopy(@NotNull DataContext dataContext) {
        String text = StringUtil.join(component.getSelection(),
                                      pluginComponent -> {
                                        IdeaPluginDescriptor descriptor = pluginComponent.getPluginDescriptor();
                                        return String.format("%s (%s)", descriptor.getName(), descriptor.getVersion());
                                      }, "\n");
        CopyPasteManager.getInstance().setContents(new TextTransferable(text));
      }

      @Override
      public boolean isCopyEnabled(@NotNull DataContext dataContext) {
        return !component.getSelection().isEmpty();
      }

      @Override
      public boolean isCopyVisible(@NotNull DataContext dataContext) {
        return true;
      }
    };

    DataManager.registerDataProvider(component, dataId -> PlatformDataKeys.COPY_PROVIDER.is(dataId) ? copyProvider : null);
  }

  public static @NotNull List<String> getTags(@NotNull IdeaPluginDescriptor plugin) {
    List<String> tags = null;
    String productCode = plugin.getProductCode();

    if (plugin instanceof PluginNode) {
      tags = ((PluginNode)plugin).getTags();

      if (productCode != null) {
        if (LicensePanel.isEA2Product(productCode)) {
          if (tags != null && tags.contains(Tags.Paid.name())) {
            tags = new ArrayList<>(tags);
            tags.remove(Tags.Paid.name());
          }
        }
        else if (tags == null) {
          return List.of(Tags.Paid.name());
        }
        else if (!tags.contains(Tags.Paid.name())) {
          tags = new ArrayList<>(tags);
          tags.add(Tags.Paid.name());
        }
      }
    }
    else if (productCode != null && !plugin.isBundled() && !LicensePanel.isEA2Product(productCode)) {
      LicensingFacade instance = LicensingFacade.getInstance();
      if (instance != null) {
        String stamp = instance.getConfirmationStamp(productCode);
        if (stamp != null) {
          return List.of(stamp.startsWith("eval:") ? Tags.Trial.name() : Tags.Purchased.name());
        }
      }
      return List.of(Tags.Paid.name());
    }
    if (ContainerUtil.isEmpty(tags)) {
      return List.of();
    }

    if (tags.size() > 1) {
      tags = new ArrayList<>(tags);
      if (tags.remove(Tags.EAP.name())) {
        tags.add(0, Tags.EAP.name());
      }
      if (tags.remove(Tags.Paid.name())) {
        tags.add(0, Tags.Paid.name());
      }
    }

    return tags;
  }

  public static <T extends Component> @NotNull T setTinyFont(@NotNull T component) {
    return SystemInfo.isMac ? RelativeFont.TINY.install(component) : component;
  }

  public static int offset5() {
    return JBUIScale.scale(5);
  }

  @Messages.YesNoResult
  public static int showRestartDialog() {
    return showRestartDialog(getUpdatesDialogTitle());
  }

  @Messages.YesNoResult
  public static int showRestartDialog(@NotNull @NlsContexts.DialogTitle String title) {
    return showRestartDialog(title, PluginManagerConfigurable::getUpdatesDialogMessage);
  }

  @Messages.YesNoResult
  public static int showRestartDialog(@NotNull @NlsContexts.DialogTitle String title,
                                      @NotNull Function<? super String, @Nls String> message) {
    String action = IdeBundle.message(ApplicationManager.getApplication().isRestartCapable() ?
                                      "ide.restart.action" :
                                      "ide.shutdown.action");
    return Messages.showYesNoDialog(message.apply(action),
                                    title,
                                    action,
                                    IdeBundle.message("ide.notnow.action"),
                                    Messages.getQuestionIcon());
  }

  public static void shutdownOrRestartApp() {
    shutdownOrRestartApp(getUpdatesDialogTitle());
  }

  public static void shutdownOrRestartApp(@NotNull @NlsContexts.DialogTitle String title) {
    shutdownOrRestartAppAfterInstall(title, PluginManagerConfigurable::getUpdatesDialogMessage);
  }

  static void shutdownOrRestartAppAfterInstall(@NotNull @NlsContexts.DialogTitle String title,
                                               @NotNull Function<? super String, @Nls String> message) {
    if (showRestartDialog(title, message) == Messages.YES) {
      ApplicationManagerEx.getApplicationEx().restart(true);
    }
  }

  static @NotNull @NlsContexts.DialogTitle String getUpdatesDialogTitle() {
    return IdeBundle.message("updates.dialog.title",
                             ApplicationNamesInfo.getInstance().getFullProductName());
  }

  static @NotNull @NlsContexts.DialogMessage String getUpdatesDialogMessage(@Nls @NotNull String action) {
    return IdeBundle.message("ide.restart.required.message",
                             action,
                             ApplicationNamesInfo.getInstance().getFullProductName());
  }

  /**
   * @deprecated Please use {@link #showPluginConfigurable(Project, Collection)}.
   */
  @Deprecated(since = "2020.2", forRemoval = true)
  public static void showPluginConfigurable(@Nullable Project project, IdeaPluginDescriptor @NotNull ... descriptors) {
    showPluginConfigurable(project,
                           ContainerUtil.map(descriptors, IdeaPluginDescriptor::getPluginId));
  }

  public static void showPluginConfigurable(@Nullable Project project,
                                            @NotNull Collection<PluginId> pluginIds) {
    PluginManagerConfigurable configurable = new PluginManagerConfigurable(project);
    ShowSettingsUtil.getInstance().editConfigurable(project,
                                                    configurable,
                                                    () -> configurable.select(pluginIds));
  }

  public static void showPluginConfigurable(@Nullable Component parent,
                                            @Nullable Project project,
                                            @NotNull Collection<PluginId> pluginIds) {
    if (parent != null) {
      PluginManagerConfigurable configurable = new PluginManagerConfigurable(project);
      ShowSettingsUtil.getInstance().editConfigurable(parent,
                                                      configurable,
                                                      () -> configurable.select(pluginIds));
    }
    else {
      showPluginConfigurable(project, pluginIds);
    }
  }

  public static void showPluginConfigurableAndEnable(@Nullable Project project,
                                                     @NotNull Set<? extends IdeaPluginDescriptor> descriptors) {
    PluginManagerConfigurable configurable = new PluginManagerConfigurable(project);
    ShowSettingsUtil.getInstance().editConfigurable(project,
                                                    configurable,
                                                    () -> {
                                                      configurable.myPluginModel.enablePlugins(descriptors);
                                                      configurable.select(descriptors);
                                                    });
  }

  private enum SortBySearchOption {
    Downloads(IdeBundle.messagePointer("plugins.configurable.SortBySearchOption.Downloads")),
    Name(IdeBundle.messagePointer("plugins.configurable.SortBySearchOption.Name")),
    Rating(IdeBundle.messagePointer("plugins.configurable.SortBySearchOption.Rating")),
    Relevance(IdeBundle.messagePointer("plugins.configurable.SortBySearchOption.Relevance")),
    Updated(IdeBundle.messagePointer("plugins.configurable.SortBySearchOption.Updated"));

    private final Supplier<@Nls String> myPresentableNameSupplier;

    SortBySearchOption(Supplier<@Nls String> supplier) {myPresentableNameSupplier = supplier;}
  }

  private final class MarketplaceSortByAction extends ToggleAction implements DumbAware {
    private final SortBySearchOption myOption;
    private boolean myState;
    private boolean myVisible;

    private MarketplaceSortByAction(@NotNull SortBySearchOption option) {
      super(option.myPresentableNameSupplier);
      myOption = option;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      super.update(e);
      e.getPresentation().setVisible(myVisible);
    }

    @Override
    public boolean isSelected(@NotNull AnActionEvent e) {
      return myState;
    }

    @Override
    public void setSelected(@NotNull AnActionEvent e, boolean state) {
      myState = state;
      myMarketplaceSortByCallback.accept(this);
    }

    public void setState(@NotNull SearchQueryParser.Marketplace parser) {
      if (myOption == SortBySearchOption.Relevance) {
        myState = parser.sortBy == null;
        myVisible = parser.sortBy == null || !parser.tags.isEmpty() || !parser.vendors.isEmpty() || parser.searchQuery != null;
      }
      else {
        myState = parser.sortBy != null && myOption.name().equalsIgnoreCase(parser.sortBy);
        myVisible = true;
      }
    }

    @Nullable
    public String getQuery() {
      switch (myOption) {
        case Downloads:
          return "/sortBy:downloads";
        case Name:
          return "/sortBy:name";
        case Rating:
          return "/sortBy:rating";
        case Updated:
          return "/sortBy:updated";
        case Relevance:
        default:
          return null;
      }
    }
  }

  private enum InstalledSearchOption {
    Downloaded(IdeBundle.messagePointer("plugins.configurable.InstalledSearchOption.Downloaded")),
    NeedUpdate(IdeBundle.messagePointer("plugins.configurable.InstalledSearchOption.NeedUpdate")),
    Enabled(IdeBundle.messagePointer("plugins.configurable.InstalledSearchOption.Enabled")),
    Disabled(IdeBundle.messagePointer("plugins.configurable.InstalledSearchOption.Disabled")),
    Invalid(IdeBundle.messagePointer("plugins.configurable.InstalledSearchOption.Invalid")),
    Bundled(IdeBundle.messagePointer("plugins.configurable.InstalledSearchOption.Bundled"));

    private final Supplier<@Nls String> myPresentableNameSupplier;

    InstalledSearchOption(Supplier<@Nls String> name) {myPresentableNameSupplier = name;}
  }

  private final class InstalledSearchOptionAction extends ToggleAction implements DumbAware {
    private final InstalledSearchOption myOption;
    private boolean myState;

    private InstalledSearchOptionAction(@NotNull InstalledSearchOption option) {
      super(option.myPresentableNameSupplier);
      myOption = option;
    }

    @Override
    public boolean isSelected(@NotNull AnActionEvent e) {
      return myState;
    }

    @Override
    public void setSelected(@NotNull AnActionEvent e, boolean state) {
      myState = state;
      myInstalledSearchCallback.accept(this);
    }

    public void setState(@Nullable SearchQueryParser.Installed parser) {
      if (parser == null) {
        myState = false;
        return;
      }

      switch (myOption) {
        case Enabled:
          myState = parser.enabled;
          break;
        case Disabled:
          myState = parser.disabled;
          break;
        case Downloaded:
          myState = parser.downloaded;
          break;
        case Bundled:
          myState = parser.bundled;
          break;
        case Invalid:
          myState = parser.invalid;
          break;
        case NeedUpdate:
          myState = parser.needUpdate;
          break;
      }
    }

    @NotNull
    public String getQuery() {
      return myOption == InstalledSearchOption.NeedUpdate ? "/outdated" : "/" + StringUtil.decapitalize(myOption.name());
    }
  }

  private static class GroupByActionGroup extends DefaultActionGroup implements CheckedActionGroup {
  }

  private final class ChangePluginStateAction extends DumbAwareAction {
    private final boolean myEnable;

    private ChangePluginStateAction(boolean enable) {
      super(enable ? IdeBundle.message("plugins.configurable.enable.all.downloaded")
                   : IdeBundle.message("plugins.configurable.disable.all.downloaded"));
      myEnable = enable;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      Set<IdeaPluginDescriptor> descriptors = new HashSet<>();
      PluginsGroup group = myPluginModel.getDownloadedGroup();

      if (group == null || group.ui == null) {
        ApplicationInfoImpl appInfo = (ApplicationInfoImpl)ApplicationInfo.getInstance();

        for (IdeaPluginDescriptor descriptor : PluginManagerCore.getPlugins()) {
          if (!appInfo.isEssentialPlugin(descriptor.getPluginId()) &&
              !descriptor.isBundled() && descriptor.isEnabled() != myEnable) {
            descriptors.add(descriptor);
          }
        }
      }
      else {
        for (ListPluginComponent component : group.ui.plugins) {
          IdeaPluginDescriptor plugin = component.getPluginDescriptor();
          if (myPluginModel.isEnabled(plugin) != myEnable) {
            descriptors.add(plugin);
          }
        }
      }

      if (!descriptors.isEmpty()) {
        if (myEnable) {
          myPluginModel.enable(descriptors);
        }
        else {
          myPluginModel.disable(descriptors);
        }
      }
    }
  }

  @NotNull
  public static JComponent createScrollPane(@NotNull PluginsGroupComponent panel, boolean initSelection) {
    JBScrollPane pane =
      new JBScrollPane(panel, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
    pane.setBorder(JBUI.Borders.empty());
    if (initSelection) {
      panel.initialSelection();
    }
    return pane;
  }

  private void addGroup(@NotNull List<? super PluginsGroup> groups,
                        @NotNull @Nls String name,
                        @NotNull PluginsGroupType type,
                        @NotNull String showAllQuery,
                        @NotNull List<PluginNode> customPlugins,
                        @NotNull Predicate<? super PluginsGroup> predicate) {
    PluginsGroup group = new PluginsGroup(name, type);

    int i = 0;
    for (Iterator<? extends IdeaPluginDescriptor> iterator = customPlugins.iterator();
         iterator.hasNext() && i < ITEMS_PER_GROUP;
         i++) {
      group.descriptors.add(iterator.next());
    }

    if (predicate.test(group)) {
      group.rightAction = new LinkLabel<>(IdeBundle.message("plugins.configurable.show.all"),
                                          null,
                                          myMarketplaceTab.mySearchListener,
                                          showAllQuery);
      group.rightAction.setBorder(JBUI.Borders.emptyRight(5));
    }

    if (!group.descriptors.isEmpty()) {
      groups.add(group);
    }
  }

  private void addGroupViaLightDescriptor(@NotNull List<? super PluginsGroup> groups,
                                          @NotNull @Nls String name,
                                          @NotNull PluginsGroupType type,
                                          @NotNull @NonNls String query,
                                          @NotNull @NonNls String showAllQuery) throws IOException {
    List<PluginNode> pluginNodes = MarketplaceRequests.getInstance().searchPlugins(query, ITEMS_PER_GROUP * 2);
    addGroup(groups,
             name,
             type,
             showAllQuery,
             pluginNodes,
             __ -> pluginNodes.size() >= ITEMS_PER_GROUP);
  }

  @Override
  @NotNull
  public String getHelpTopic() {
    return ID;
  }

  @Override
  public void disposeUIResources() {
    InstalledPluginsState pluginsState = InstalledPluginsState.getInstance();
    if (myPluginModel.toBackground()) {
      pluginsState.clearShutdownCallback();
    }

    myMarketplaceTab.dispose();
    myInstalledTab.dispose();

    if (myMarketplacePanel != null) {
      myMarketplacePanel.dispose();
    }
    if (myMarketplaceSearchPanel != null) {
      myMarketplaceSearchPanel.dispose();
    }

    myPluginUpdatesService.dispose();
    PluginPriceService.cancel();

    pluginsState.runShutdownCallback();
    pluginsState.resetChangesAppliedWithoutRestart();
  }

  @Override
  public void cancel() {
    myPluginModel.cancel(myCardPanel);
  }

  @Override
  public boolean isModified() {
    return myPluginModel.isModified();
  }

  @Override
  public void apply() throws ConfigurationException {
    if (myPluginModel.apply(myCardPanel)) return;

    if (myPluginModel.createShutdownCallback) {
      InstalledPluginsState.getInstance().setShutdownCallback(() -> {
        ApplicationManager.getApplication().invokeLater(() -> {
          shutdownOrRestartApp();
        });
      });
    }
  }

  @Override
  public void reset() {
    myPluginModel.clear(myCardPanel);
  }

  /**
   * @deprecated Please use {@link #select(Collection)}.
   */
  @Deprecated(since = "2020.2", forRemoval = true)
  public void select(@NotNull IdeaPluginDescriptor @NotNull ... descriptors) {
    select(ContainerUtil.newHashSet(descriptors));
  }

  private void select(@NotNull Set<? extends IdeaPluginDescriptor> descriptors) {
    select(ContainerUtil.map(descriptors, IdeaPluginDescriptor::getPluginId));
  }

  private void select(@NotNull Collection<PluginId> pluginIds) {
    updateSelectionTab(INSTALLED_TAB);

    List<ListPluginComponent> components = new ArrayList<>();

    for (PluginId pluginId : pluginIds) {
      ListPluginComponent component = findInstalledPluginById(pluginId);
      if (component != null) {
        components.add(component);
      }
    }

    if (!components.isEmpty()) {
      myInstalledPanel.setSelection(components);
    }
  }

  @Nullable
  @Override
  public Runnable enableSearch(String option) {
    if (myTabHeaderComponent == null) {
      myLaterSearchQuery = option;
      return () -> {};
    }
    if (StringUtil.isEmpty(option) && (myTabHeaderComponent.getSelectionTab() == MARKETPLACE_TAB || myInstalledSearchPanel.isEmpty())) {
      return null;
    }

    return () -> {
      boolean marketplace = (option != null && option.startsWith(SearchWords.TAG.getValue()));
      if (myShowMarketplaceTab) {
        marketplace = true;
        myShowMarketplaceTab = false;
      }
      updateSelectionTab(marketplace ? MARKETPLACE_TAB : INSTALLED_TAB);

      PluginsTab tab = marketplace ? myMarketplaceTab : myInstalledTab;
      tab.clearSearchPanel(option);

      if (!StringUtil.isEmpty(option)) {
        tab.showSearchPanel(option);
      }
    };
  }

  public void openMarketplaceTab(@NotNull String option) {
    myLaterSearchQuery = option;
    myShowMarketplaceTab = true;
    if (myTabHeaderComponent != null) {
      updateSelectionTab(MARKETPLACE_TAB);
    }
    if (myMarketplaceTab != null) {
      myMarketplaceTab.clearSearchPanel(option);
      myMarketplaceTab.showSearchPanel(option);
    }
  }

  private final class InstallFromDiskAction extends DumbAwareAction {
    private InstallFromDiskAction() {
      super(IdeBundle.messagePointer("action.InstallFromDiskAction.text"));
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      if (!PluginManagerFilters.getInstance().allowInstallFromDisk()) {
        Messages.showErrorDialog(e.getProject(), IdeBundle.message("action.InstallFromDiskAction.not.allowed.description"), IdeBundle.message("action.InstallFromDiskAction.text"));
        return;
      }

      PluginInstaller.chooseAndInstall(e.getProject(), myCardPanel, (file, parent) ->
        PluginInstaller.installFromDisk(myPluginModel, myPluginModel, file, parent, callbackData -> {
          myPluginModel.pluginInstalledFromDisk(callbackData);

          boolean select = myInstalledPanel == null;
          updateSelectionTab(INSTALLED_TAB);

          myInstalledTab.clearSearchPanel("");

          ListPluginComponent component = select ?
                                          findInstalledPluginById(callbackData.getPluginDescriptor().getPluginId()) :
                                          null;
          if (component != null) {
            myInstalledPanel.setSelection(component);
          }
        }));
    }
  }

  private void updateSelectionTab(int tab) {
    if (myTabHeaderComponent.getSelectionTab() != tab) {
      myTabHeaderComponent.setSelectionWithEvents(tab);
    }
  }

  private @NotNull List<UIPluginGroup> getInstalledGroups() {
    return myInstalledPanel.getGroups();
  }

  private @Nullable ListPluginComponent findInstalledPluginById(@NotNull PluginId pluginId) {
    for (UIPluginGroup group : getInstalledGroups()) {
      ListPluginComponent component = group.findComponent(pluginId);
      if (component != null) {
        return component;
      }
    }
    return null;
  }
}
