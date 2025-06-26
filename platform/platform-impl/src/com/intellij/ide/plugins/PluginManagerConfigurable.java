// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins;

import com.intellij.execution.process.ProcessIOExecutorService;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.icons.AllIcons;
import com.intellij.ide.CopyProvider;
import com.intellij.ide.DataManager;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.impl.ProjectUtil;
import com.intellij.ide.plugins.certificates.PluginCertificateManager;
import com.intellij.ide.plugins.enums.PluginsGroupType;
import com.intellij.ide.plugins.enums.SortBy;
import com.intellij.ide.plugins.marketplace.PluginSearchResult;
import com.intellij.ide.plugins.newui.UiPluginManager;
import com.intellij.ide.plugins.marketplace.ranking.MarketplaceLocalRanker;
import com.intellij.ide.plugins.marketplace.statistics.PluginManagerUsageCollector;
import com.intellij.ide.plugins.newui.*;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.idea.AppMode;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.impl.PresentationFactory;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.DumbAwareToggleAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupListener;
import com.intellij.openapi.ui.popup.LightweightWindowEvent;
import com.intellij.openapi.updateSettings.impl.*;
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.FUSEventSource;
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.PluginsAdvertiserStartupActivityKt;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.*;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextField;
import com.intellij.ui.components.fields.ExtendableTextComponent;
import com.intellij.ui.components.labels.LinkLabel;
import com.intellij.ui.components.labels.LinkListener;
import com.intellij.ui.popup.ActionPopupOptions;
import com.intellij.ui.popup.PopupFactoryImpl;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.net.HttpConfigurable;
import com.intellij.util.ui.*;
import kotlinx.coroutines.CoroutineScope;
import kotlinx.coroutines.CoroutineScopeKt;
import kotlinx.coroutines.Dispatchers;
import org.jetbrains.annotations.*;

import javax.accessibility.AccessibleContext;
import javax.accessibility.AccessibleRole;
import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static com.intellij.ide.plugins.newui.PluginsViewCustomizerKt.getPluginsViewCustomizer;

@ApiStatus.Internal
public final class PluginManagerConfigurable
  implements SearchableConfigurable, Configurable.NoScroll, Configurable.NoMargin, Configurable.TopComponentProvider {

  private static final Logger LOG = Logger.getInstance(PluginManagerConfigurable.class);

  public static final String ID = "preferences.pluginManager";
  public static final String SELECTION_TAB_KEY = "PluginConfigurable.selectionTab";

  @SuppressWarnings("UseJBColor") public static final Color MAIN_BG_COLOR =
    JBColor.namedColor("Plugins.background", JBColor.lazy(() -> JBColor.isBright() ? UIUtil.getListBackground() : new Color(0x313335)));
  public static final Color SEARCH_BG_COLOR = JBColor.namedColor("Plugins.SearchField.background", MAIN_BG_COLOR);
  public static final Color SEARCH_FIELD_BORDER_COLOR = JBColor.namedColor("Plugins.borderColor", JBColor.border());

  private static final int MARKETPLACE_TAB = 0;
  private static final int INSTALLED_TAB = 1;

  public static final int ITEMS_PER_GROUP = 9;

  public static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("MMM dd, yyyy");

  private TabbedPaneHeaderComponent myTabHeaderComponent;
  private MultiPanel myCardPanel;

  private PluginsTab myMarketplaceTab;
  private PluginsTab myInstalledTab;

  private PluginsGroupComponentWithProgress myMarketplacePanel;
  private PluginsGroupComponentWithProgress myInstalledPanel;

  private final PluginsGroup myBundledUpdateGroup =
    new PluginsGroup(IdeBundle.message("plugins.configurable.bundled.updates"), PluginsGroupType.BUNDLED_UPDATE);

  private Runnable myMarketplaceRunnable;

  private SearchResultPanel myMarketplaceSearchPanel;
  private SearchResultPanel myInstalledSearchPanel;

  private final LinkLabel<Object> myUpdateAll = new LinkLabelButton<>(IdeBundle.message("plugin.manager.update.all"), null);
  private final LinkLabel<Object> myUpdateAllBundled = new LinkLabelButton<>(IdeBundle.message("plugin.manager.update.all"), null);
  private final JLabel myUpdateCounter = new CountComponent();
  private final JLabel myUpdateCounterBundled = new CountComponent();
  private final CountIcon myCountIcon = new CountIcon();
  private final CoroutineScope myCoroutineScope;

  private final PluginModelFacade myPluginModelFacade;

  private PluginUpdatesService myPluginUpdatesService;

  private PluginManagerCustomizer myPluginManagerCustomizer;

  private List<String> myTagsSorted;
  private List<String> myVendorsSorted;

  private DefaultActionGroup myMarketplaceSortByGroup;
  private Consumer<MarketplaceSortByAction> myMarketplaceSortByCallback;
  private LinkComponent myMarketplaceSortByAction;

  private DefaultActionGroup myInstalledSearchGroup;
  private Consumer<InstalledSearchOptionAction> myInstalledSearchCallback;
  private boolean myInstalledSearchSetState = true;

  private String myLaterSearchQuery;
  private boolean myForceShowInstalledTabForTag = false;
  private boolean myShowMarketplaceTab;

  private Boolean myPluginsAutoUpdateEnabled;

  private Disposable myDisposer = Disposer.newDisposable();

  /**
   * @deprecated Use {@link PluginManagerConfigurable#PluginManagerConfigurable()}
   */
  @Deprecated
  public PluginManagerConfigurable(@Nullable Project project) {
    this();
  }

  public PluginManagerConfigurable() {
    myPluginModelFacade = new PluginModelFacade(new MyPluginModel(null));
    myPluginManagerCustomizer = PluginManagerCustomizer.getInstance();
    CoroutineScope parentScope =
      ApplicationManager.getApplication().getService(PluginManagerCoroutineScopeHolder.class).getCoroutineScope();
    CoroutineScope childScope =
      com.intellij.platform.util.coroutines.CoroutineScopeKt.childScope(parentScope, getClass().getName(), Dispatchers.getIO(), true);
    myPluginModelFacade.getModel().setCoroutineScope(childScope);
    myCoroutineScope = childScope;
  }

  @Override
  public @NotNull String getId() {
    return ID;
  }

  @Override
  public String getDisplayName() {
    return IdeBundle.message("title.plugins");
  }

  @Override
  public @NotNull JComponent getCenterComponent(@NotNull TopComponentController controller) {
    myPluginModelFacade.getModel().setTopController(controller);
    return myTabHeaderComponent;
  }

  public @NotNull JComponent getTopComponent() {
    return getCenterComponent(TopComponentController.EMPTY);
  }

  @Override
  public @Nullable JComponent createComponent() {
    myTabHeaderComponent = new TabbedPaneHeaderComponent(createGearActions(), index -> {
      myCardPanel.select(index, true);
      storeSelectionTab(index);

      String query = (index == MARKETPLACE_TAB ? myInstalledTab : myMarketplaceTab).getSearchQuery();
      (index == MARKETPLACE_TAB ? myMarketplaceTab : myInstalledTab).setSearchQuery(query);
    });
    createGearGotIt();

    myUpdateAll.setVisible(false);
    myUpdateAllBundled.setVisible(false);
    myUpdateCounter.setVisible(false);
    myUpdateCounterBundled.setVisible(false);

    myTabHeaderComponent.addTab(IdeBundle.message("plugin.manager.tab.marketplace"), null);
    myTabHeaderComponent.addTab(IdeBundle.message("plugin.manager.tab.installed"), myCountIcon);

    CustomPluginRepositoryService.getInstance().clearCache();
    myPluginUpdatesService =
      UiPluginManager.getInstance().subscribeToUpdatesCount(myPluginModelFacade.getModel().getSessionId(), countValue -> {
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
        return null;
      });
    myPluginModelFacade.getModel().setPluginUpdatesService(myPluginUpdatesService);

    UiPluginManager.getInstance().updateDescriptorsForInstalledPlugins();

    createMarketplaceTab();
    createInstalledTab();

    PluginManagerUsageCollector.sessionStarted();

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
      Runnable search = enableSearch(myLaterSearchQuery, myForceShowInstalledTabForTag);
      if (search != null) {
        ApplicationManager.getApplication().invokeLater(search, ModalityState.any());
      }
      myLaterSearchQuery = null;
      myForceShowInstalledTabForTag = false;
    }

    getPluginsViewCustomizer().processConfigurable(this);

    return myCardPanel;
  }

  private @NotNull DefaultActionGroup createGearActions() {
    DefaultActionGroup actions = new DefaultActionGroup();
    if (PluginManagementPolicy.getInstance().isPluginAutoUpdateAllowed()) {
      UpdateOptions state = UpdateSettings.getInstance().getState();
      myPluginsAutoUpdateEnabled = state.isPluginsAutoUpdateEnabled();

      MessageBusConnection connect =
        ApplicationManager.getApplication().getMessageBus().connect(com.intellij.util.CoroutineScopeKt.asDisposable(myCoroutineScope));
      connect.subscribe(PluginAutoUpdateListener.Companion.getTOPIC(), new PluginAutoUpdateListener() {
        @Override
        public void settingsChanged() {
          myPluginsAutoUpdateEnabled = state.isPluginsAutoUpdateEnabled();
        }
      });

      actions.add(new DumbAwareToggleAction(IdeBundle.message("updates.plugins.autoupdate.settings.action")) {
        @Override
        public boolean isSelected(@NotNull AnActionEvent e) {
          return myPluginsAutoUpdateEnabled;
        }

        @Override
        public void setSelected(@NotNull AnActionEvent e, boolean state) {
          myPluginsAutoUpdateEnabled = state;
        }

        @Override
        public @NotNull ActionUpdateThread getActionUpdateThread() {
          return ActionUpdateThread.EDT;
        }
      });
      actions.addSeparator();
    }
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

    actions.add(new InstallFromDiskAction(myPluginModelFacade.getModel(),
                                          myPluginModelFacade.getModel(),
                                          myCardPanel) {

      @RequiresEdt
      @Override
      protected void onPluginInstalledFromDisk(@NotNull PluginInstallCallbackData callbackData,
                                               @Nullable Project project) {
        PluginManagerConfigurable.this.onPluginInstalledFromDisk(callbackData);
      }
    });
    actions.addSeparator();
    actions.add(new ChangePluginStateAction(false));
    actions.add(new ChangePluginStateAction(true));

    if (ApplicationManager.getApplication().isInternal()) {
      actions.addSeparator();
      actions.add(new DumbAwareAction(IdeBundle.message("plugin.manager.refresh")) {
        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
          resetPanels();
        }
      });
    }
    return actions;
  }

  private void createGearGotIt() {
    if (!PluginManagementPolicy.getInstance().isPluginAutoUpdateAllowed() ||
        UpdateSettings.getInstance().getState().isPluginsAutoUpdateEnabled() ||
        AppMode.isRemoteDevHost()) {
      return;
    }

    String title = IdeBundle.message("plugin.manager.plugins.auto.update.title");
    GotItTooltip tooltip = new GotItTooltip(title, IdeBundle.message("plugin.manager.plugins.auto.update.description"), myDisposer);
    tooltip.withHeader(title);
    tooltip.show((JComponent)myTabHeaderComponent.getComponent(1), (component, balloon) -> {
      return new Point(component.getWidth() / 2, ((JComponent)component).getVisibleRect().height);
    });
  }

  private static void showRightBottomPopup(@NotNull Component component, @NotNull @Nls String title, @NotNull ActionGroup group) {
    DefaultActionGroup actions = new GroupByActionGroup();
    actions.addSeparator("  " + title);
    actions.addAll(group);

    DataContext context = DataManager.getInstance().getDataContext(component);

    JBPopup popup = new PopupFactoryImpl.ActionGroupPopup(
      null, null, actions, context, ActionPlaces.POPUP, new PresentationFactory(),
      ActionPopupOptions.honorMnemonics(), null) {
    };
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
                                                                     @NotNull List<HtmlChunk> errors) {
            return new ListPluginComponent(myPluginModelFacade, model, group, searchListener, errors, true);
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
        PluginManagerPanelFactory.INSTANCE.createMarketplacePanel(myCoroutineScope, myPluginModelFacade.getModel(), project,  model -> {
          List<PluginsGroup> groups = new ArrayList<>();
          try {
            Map<String, List<PluginUiModel>> customRepositoriesMap = UiPluginManager.getInstance().getCustomRepositoryPluginMap();
            try {
              if (project != null) {
                addSuggestedGroup(groups, project, customRepositoriesMap, model.getErrors(), model.getSuggestedPlugins());
              }

              PluginsViewCustomizer.PluginsGroupDescriptor internalPluginsGroupDescriptor =
                getPluginsViewCustomizer().getInternalPluginsGroupDescriptor();
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
                  model.getErrors()
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
                model.getErrors()
              );
              addGroupViaLightDescriptor(
                groups,
                IdeBundle.message("plugins.configurable.new.and.updated"),
                PluginsGroupType.NEW_AND_UPDATED,
                "orderBy=update+date",
                "/sortBy:updated",
                marketplaceData,
                model.getErrors()
              );
              addGroupViaLightDescriptor(
                groups,
                IdeBundle.message("plugins.configurable.top.downloads"),
                PluginsGroupType.TOP_DOWNLOADS,
                "orderBy=downloads",
                "/sortBy:downloads",
                marketplaceData,
                model.getErrors()
              );
              addGroupViaLightDescriptor(
                groups,
                IdeBundle.message("plugins.configurable.top.rated"),
                PluginsGroupType.TOP_RATED,
                "orderBy=rating",
                "/sortBy:rating",
                marketplaceData,
                model.getErrors()
              );
            }
            catch (IOException e) {
              LOG.info("Main plugin repository is not available ('" + e.getMessage() + "'). Please check your network settings.");
            }

            for (String host : RepositoryHelper.getCustomPluginRepositoryHosts()) {
              List<PluginUiModel> allDescriptors = customRepositoriesMap.get(host);
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
                         model.getErrors());
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
            if (getPluginsViewCustomizer() != NoOpPluginsViewCustomizer.INSTANCE) {
              attributes.add(SearchWords.INTERNAL.getValue());
            }
            return attributes;
          }

          @Override
          protected @Nullable List<String> getValues(@NotNull String attribute) {
            SearchWords word = SearchWords.find(attribute);
            if (word == null) return null;
            return switch (word) {
              case TAG -> {
                if (myTagsSorted == null || myTagsSorted.isEmpty()) {
                  Set<String> allTags = new HashSet<>();
                  for (PluginUiModel descriptor : UiPluginManager.getInstance().getCustomRepoPlugins()) {
                    List<String> tags = descriptor.getTags();
                    if (tags != null && !tags.isEmpty()) {
                      allTags.addAll(tags);
                    }
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
                yield myTagsSorted;
              }
              case SORT_BY -> ContainerUtil.map(
                Arrays.asList(SortBy.DOWNLOADS, SortBy.NAME, SortBy.RATING, SortBy.UPDATE_DATE),
                sort -> sort.getQuery()
              );
              case VENDOR -> {
                if (myVendorsSorted == null || myVendorsSorted.isEmpty()) {
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
                yield myVendorsSorted;
              }
              case REPOSITORY -> RepositoryHelper.getCustomPluginRepositoryHosts();
              case INTERNAL, SUGGESTED, STAFF_PICKS -> null;
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

        myMarketplaceSortByGroup = new DefaultActionGroup();

        for (SortBy option : SortBy.getEntries()) {
          myMarketplaceSortByGroup.addAction(new MarketplaceSortByAction(option));
        }

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

        myMarketplaceSortByCallback = updateAction -> {
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
            if (updateAction.myOption == SortBy.RELEVANCE) {
              updateAction.myState = true;
              return;
            }

            for (AnAction action : myMarketplaceSortByGroup.getChildren(ActionManager.getInstance())) {
              MarketplaceSortByAction sortByAction = (MarketplaceSortByAction)action;
              if (sortByAction.myOption == SortBy.RELEVANCE) {
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
          protected @NotNull ListPluginComponent createListComponent(@NotNull PluginUiModel model,
                                                                     @NotNull PluginsGroup group,
                                                                     @NotNull List<HtmlChunk> errors) {
            return new ListPluginComponent(myPluginModelFacade, model, group, searchListener, errors, true);
          }
        };

        panel.setSelectionListener(selectionListener);
        registerCopyProvider(panel);

        Project project = ProjectUtil.getActiveProject();

        myMarketplaceSearchPanel =
          new SearchResultPanel(marketplaceController, panel, true, 0, 0) {
            @Override
            protected void handleQuery(@NotNull String query, @NotNull PluginsGroup result) {
              int searchIndex = PluginManagerUsageCollector.updateAndGetSearchIndex();

              SearchQueryParser.Marketplace parser = new SearchQueryParser.Marketplace(query);

              Map<PluginUiModel, Double> pluginToScore = null;

              if (parser.internal) {
                PluginsViewCustomizer.PluginsGroupDescriptor groupDescriptor =
                  getPluginsViewCustomizer().getInternalPluginsGroupDescriptor();
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

              Map<String, List<PluginUiModel>> customRepositoriesMap =
                CustomPluginRepositoryService.getInstance().getCustomRepositoryPluginMap();


              if (parser.suggested && project != null) {
                result.addModels(PluginsAdvertiserStartupActivityKt.findSuggestedPlugins(project, customRepositoriesMap));
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
              }
              else {
                PluginSearchResult searchResult = UiPluginManager.getInstance().executeMarketplaceQuery(parser.getUrlQuery(), 10000, true);
                if (searchResult.getError() != null) {
                  ApplicationManager.getApplication().invokeLater(
                    () -> myPanel.getEmptyText()
                      .setText(IdeBundle.message("plugins.configurable.search.result.not.loaded"))
                      .appendSecondaryText(
                        IdeBundle.message("plugins.configurable.check.internet"), StatusText.DEFAULT_ATTRIBUTES, null), ModalityState.any()
                  );
                }
                // compare plugin versions between marketplace & custom repositories
                List<PluginUiModel> customPlugins = ContainerUtil.flatten(customRepositoriesMap.values());
                Collection<PluginUiModel> plugins = RepositoryHelper.mergePluginModelsFromRepositories(searchResult.getPlugins(),
                                                                                                       customPlugins,
                                                                                                       false);
                result.addModels(0, new ArrayList<>(plugins));

                if (parser.searchQuery != null) {
                  List<PluginUiModel> descriptors = ContainerUtil.filter(customPlugins,
                                                                         descriptor -> StringUtil.containsIgnoreCase(descriptor.getName(),
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


                  Collection<PluginUiModel> updates = UiPluginManager.getInstance().getUpdateModels();
                  if (!ContainerUtil.isEmpty(updates)) {
                    myPostFillGroupCallback = () -> {
                      applyUpdates(myPanel, updates);
                      selectionListener.accept(myMarketplacePanel);
                      selectionListener.accept(myMarketplaceSearchPanel.getPanel());
                    };
                  }
                }
              }

              PluginManagerUsageCollector.INSTANCE.performMarketplaceSearch(
                ProjectUtil.getActiveProject(), parser, result.getModels(), searchIndex, pluginToScore);
            }
          };
        return myMarketplaceSearchPanel;
      }

      @Override
      protected void onSearchReset() {
        PluginManagerUsageCollector.INSTANCE.searchReset();
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

        JBTextField textField = searchTextField.getTextEditor();
        textField.putClientProperty("search.extension", ExtendableTextComponent.Extension
          .create(AllIcons.Actions.More, AllIcons.Actions.More, IdeBundle.message("plugins.configurable.search.options"), // TODO: icon
                  () -> showRightBottomPopup(textField, IdeBundle.message("plugins.configurable.show"), myInstalledSearchGroup)));
        textField.putClientProperty("JTextField.variant", null);
        textField.putClientProperty("JTextField.variant", "search");

        searchTextField.setHistoryPropertyName("InstalledPluginsSearchHistory");
      }

      @Override
      protected @NotNull PluginDetailsPageComponent createDetailsPanel(@NotNull LinkListener<Object> searchListener) {
        PluginDetailsPageComponent detailPanel = new PluginDetailsPageComponent(myPluginModelFacade, searchListener, false);
        myPluginModelFacade.getModel().addDetailPanel(detailPanel);
        return detailPanel;
      }

      @Override
      protected @NotNull JComponent createPluginsPanel(@NotNull Consumer<? super PluginsGroupComponent> selectionListener) {
        MultiSelectionEventHandler eventHandler = new MultiSelectionEventHandler();
        UiPluginManager uiPluginManager = UiPluginManager.getInstance();
        myInstalledPanel = new PluginsGroupComponentWithProgress(eventHandler) {
          @Override
          protected @NotNull ListPluginComponent createListComponent(@NotNull PluginUiModel model,
                                                                     @NotNull PluginsGroup group,
                                                                     @NotNull List<HtmlChunk> errors) {
            return new ListPluginComponent(myPluginModelFacade, model, group, searchListener, errors, false);
          }
        };

        myInstalledPanel.setSelectionListener(selectionListener);
        myInstalledPanel.getAccessibleContext().setAccessibleName(IdeBundle.message("plugin.manager.installed.panel.accessible.name"));
        registerCopyProvider(myInstalledPanel);

        //noinspection ConstantConditions
        ((SearchUpDownPopupController)myInstalledSearchPanel.controller).setEventHandler(eventHandler);
        myInstalledPanel.startLoading();
        PluginManagerPanelFactory.INSTANCE.createInstalledPanel(myCoroutineScope, myPluginModelFacade.getModel(), model -> {
          try {
            PluginsGroup installing = new PluginsGroup(IdeBundle.message("plugins.configurable.installing"), PluginsGroupType.INSTALLING);
            installing.setErrors(model.getErrors());
            installing.addModels(MyPluginModel.getInstallingPlugins());
            if (!installing.getModels().isEmpty()) {
              installing.sortByName();
              installing.titleWithCount();
              myInstalledPanel.addGroup(installing);
            }

            PluginsGroup downloaded =
              new PluginsGroup(IdeBundle.message("plugins.configurable.downloaded"), PluginsGroupType.INSTALLED);
            downloaded.setErrors(model.getErrors());
            downloaded.addModels(model.getInstalledPlugins());

            myBundledUpdateGroup.setErrors(model.getErrors());

            Map<Boolean, List<PluginUiModel>> visiblePlugins = model.getVisiblePlugins()
              .stream()
              .collect(Collectors.partitioningBy(PluginUiModel::isBundled));

            List<PluginUiModel> nonBundledPlugins = visiblePlugins.get(Boolean.FALSE);
            downloaded.addModels(nonBundledPlugins);

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

            if (!downloaded.getModels().isEmpty()) {
              downloaded.sortByName();

              long enabledNonBundledCount = nonBundledPlugins.stream()
                .filter(descriptor -> !uiPluginManager.isPluginDisabled(descriptor.getPluginId()))
                .count();
              downloaded.titleWithCount(Math.toIntExact(enabledNonBundledCount));
              myInstalledPanel.addGroup(downloaded);
              myPluginModelFacade.getModel().addEnabledGroup(downloaded);
            }

            myPluginModelFacade.getModel().setDownloadedGroup(myInstalledPanel, downloaded, installing);

            String defaultCategory = IdeBundle.message("plugins.configurable.other.bundled");
            visiblePlugins.get(Boolean.TRUE)
              .stream()
              .collect(Collectors.groupingBy(descriptor -> StringUtil.defaultIfEmpty(descriptor.getDisplayCategory(), defaultCategory)))
              .entrySet()
              .stream()
              .map(entry -> new ComparablePluginsGroup(entry.getKey(), entry.getValue()))
              .sorted((o1, o2) -> defaultCategory.equals(o1.title) ? 1 :
                                  defaultCategory.equals(o2.title) ? -1 :
                                  o1.compareTo(o2))
              .forEachOrdered(group -> {
                group.setErrors(model.getErrors());
                myInstalledPanel.addGroup(group);
                myPluginModelFacade.getModel().addEnabledGroup(group);
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
              selectionListener.accept(myInstalledSearchPanel.getPanel());
            });
          }
          finally {
            myInstalledPanel.stopLoading();
          }
          return null;
        });

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
          for (AnAction action : myInstalledSearchGroup.getChildren(ActionManager.getInstance())) {
            ((InstalledSearchOptionAction)action).setState(null);
          }
        }
        myPluginModelFacade.getModel().setInvalidFixCallback(null);
      }

      @Override
      protected void onSearchReset() {
        PluginManagerUsageCollector.INSTANCE.searchReset();
      }

      @Override
      protected @NotNull SearchResultPanel createSearchPanel(@NotNull Consumer<? super PluginsGroupComponent> selectionListener) {
        SearchUpDownPopupController installedController = new SearchUpDownPopupController(searchTextField) {
          @Override
          protected @NotNull @NonNls List<String> getAttributes() {
            return Arrays
              .asList(
                "/downloaded",
                "/outdated",
                "/enabled",
                "/disabled",
                "/invalid",
                "/bundled",
                SearchWords.VENDOR.getValue(),
                SearchWords.TAG.getValue()
              );
          }

          @Override
          protected @Nullable SortedSet<String> getValues(@NotNull String attribute) {
            return SearchWords.VENDOR.getValue().equals(attribute) ?
                   myPluginModelFacade.getModel().getVendors() :
                   SearchWords.TAG.getValue().equals(attribute) ?
                   myPluginModelFacade.getModel().getTags() :
                   null;
          }

          @Override
          protected void showPopupForQuery() {
            showSearchPanel(searchTextField.getText());
          }
        };

        MultiSelectionEventHandler eventHandler = new MultiSelectionEventHandler();
        installedController.setSearchResultEventHandler(eventHandler);

        PluginsGroupComponent panel = new PluginsGroupComponent(eventHandler) {
          @Override
          protected @NotNull ListPluginComponent createListComponent(@NotNull PluginUiModel model,
                                                                     @NotNull PluginsGroup group,
                                                                     @NotNull List<HtmlChunk> errors) {
            return new ListPluginComponent(myPluginModelFacade, model, group, searchListener, errors, false);
          }
        };

        panel.setSelectionListener(selectionListener);
        registerCopyProvider(panel);

        myInstalledSearchCallback = updateAction -> {
          List<String> queries = new ArrayList<>();
          new SearchQueryParser.Installed(searchTextField.getText()) {
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
            for (AnAction action : myInstalledSearchGroup.getChildren(ActionManager.getInstance())) {
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
            searchTextField.setTextIgnoreEvents(query);
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
            int searchIndex = PluginManagerUsageCollector.updateAndGetSearchIndex();
            myPluginModelFacade.getModel().setInvalidFixCallback(null);

            SearchQueryParser.Installed parser = new SearchQueryParser.Installed(query);

            if (myInstalledSearchSetState) {
              for (AnAction action : myInstalledSearchGroup.getChildren(ActionManager.getInstance())) {
                ((InstalledSearchOptionAction)action).setState(parser);
              }
            }

            List<PluginUiModel> descriptors = myPluginModelFacade.getModel().getInstalledDescriptors();

            if (!parser.vendors.isEmpty()) {
              for (Iterator<PluginUiModel> I = descriptors.iterator(); I.hasNext(); ) {
                if (!MyPluginModel.isVendor(I.next(), parser.vendors)) {
                  I.remove();
                }
              }
            }
            if (!parser.tags.isEmpty()) {
              for (Iterator<PluginUiModel> I = descriptors.iterator(); I.hasNext(); ) {
                if (!ContainerUtil.intersects(PluginUiModelKt.calculateTags(I.next()), parser.tags)) {
                  I.remove();
                }
              }
            }
            for (Iterator<PluginUiModel> I = descriptors.iterator(); I.hasNext(); ) {
              PluginUiModel descriptor = I.next();
              if (parser.attributes) {
                if (parser.enabled &&
                    (!myPluginModelFacade.isEnabled(descriptor) || !myPluginModelFacade.getErrors(descriptor).isEmpty())) {
                  I.remove();
                  continue;
                }
                if (parser.disabled &&
                    (myPluginModelFacade.isEnabled(descriptor) || !myPluginModelFacade.getErrors(descriptor).isEmpty())) {
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
                if (parser.invalid && myPluginModelFacade.getErrors(descriptor).isEmpty()) {
                  I.remove();
                  continue;
                }
                if (parser.needUpdate && !UiPluginManager.getInstance().isNeedUpdate(descriptor.getPluginId())) {
                  I.remove();
                  continue;
                }
              }
              if (parser.searchQuery != null && !containsQuery(descriptor, parser.searchQuery)) {
                I.remove();
              }
            }

            result.addModels(descriptors);
            PluginManagerUsageCollector.performInstalledTabSearch(
              ProjectUtil.getActiveProject(), parser, result.getModels(), searchIndex, null);

            if (!result.getModels().isEmpty()) {
              if (parser.invalid) {
                myPluginModelFacade.getModel().setInvalidFixCallback(() -> {
                  PluginsGroup group = myInstalledSearchPanel.getGroup();
                  if (group.ui == null) {
                    myPluginModelFacade.getModel().setInvalidFixCallback(null);
                    return;
                  }

                  PluginsGroupComponent resultPanel = myInstalledSearchPanel.getPanel();

                  for (PluginUiModel descriptor : new ArrayList<>(group.getModels())) {
                    if (myPluginModelFacade.getErrors(descriptor).isEmpty()) {
                      resultPanel.removeFromGroup(group, descriptor);
                    }
                  }

                  group.titleWithCount();
                  myInstalledSearchPanel.fullRepaint();

                  if (group.getModels().isEmpty()) {
                    myPluginModelFacade.getModel().setInvalidFixCallback(null);
                    myInstalledSearchPanel.removeGroup();
                  }
                });
              }
              else if (parser.needUpdate) {
                result.rightAction = new LinkLabelButton<>(IdeBundle.message("plugin.manager.update.all"), null, (__, ___) -> {
                  result.rightAction.setEnabled(false);

                  for (ListPluginComponent plugin : result.ui.plugins) {
                    plugin.updatePlugin();
                  }
                });
              }

              Collection<PluginUiModel> updates = UiPluginManager.getInstance().getUpdateModels();
              if (!ContainerUtil.isEmpty(updates)) {
                myPostFillGroupCallback = () -> {
                  applyUpdates(myPanel, updates);
                  selectionListener.accept(myInstalledPanel);
                  selectionListener.accept(myInstalledSearchPanel.getPanel());
                };
              }
            }
          }
        };

        return myInstalledSearchPanel;
      }
    };

    myPluginModelFacade.getModel().setCancelInstallCallback(descriptor -> {
      if (myInstalledSearchPanel == null) {
        return;
      }

      PluginsGroup group = myInstalledSearchPanel.getGroup();

      if (group.ui != null && group.ui.findComponent(descriptor.getPluginId()) != null) {
        myInstalledSearchPanel.getPanel().removeFromGroup(group, descriptor);
        group.titleWithCount();
        myInstalledSearchPanel.fullRepaint();

        if (group.getModels().isEmpty()) {
          myInstalledSearchPanel.removeGroup();
        }
      }
    });
  }

  private void addSuggestedGroup(@NotNull List<? super PluginsGroup> groups,
                                 @NotNull Project project,
                                 Map<String, @NotNull List<PluginUiModel>> customMap,
                                 @NotNull Map<@NotNull PluginId,
                                 @NotNull List<@NotNull HtmlChunk>> errors,
                                 @NotNull List<@NotNull PluginUiModel> plugins) {
    String groupName = IdeBundle.message("plugins.configurable.suggested");
    LOG.info("Marketplace tab: '" + groupName + "' group load started");

    for (PluginUiModel plugin : plugins) {
      if (plugin.isFromMarketplace()) {
        plugin.setInstallSource(FUSEventSource.PLUGINS_SUGGESTED_GROUP);
      }

      FUSEventSource.PLUGINS_SUGGESTED_GROUP.logPluginSuggested(plugin.getPluginId());
    }
    addGroup(groups, groupName, PluginsGroupType.SUGGESTED, "", plugins, group -> false, errors);
  }


  private final class ComparablePluginsGroup extends PluginsGroup
    implements Comparable<ComparablePluginsGroup> {

    private boolean myIsEnable = false;

    private ComparablePluginsGroup(@NotNull @NlsSafe String category,
                                   @NotNull List<PluginUiModel> descriptors) {
      super(category, PluginsGroupType.INSTALLED);

      this.addModels(descriptors);
      sortByName();

      rightAction = new LinkLabelButton<>("",
                                          null,
                                          (__, ___) -> setEnabledState());
      boolean hasPluginsAvailableForEnableDisable =
        UiPluginManager.getInstance().hasPluginsAvailableForEnableDisable(ContainerUtil.map(descriptors, PluginUiModel::getPluginId));
      rightAction.setVisible(hasPluginsAvailableForEnableDisable);
      titleWithEnabled(myPluginModelFacade);
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
      setState(myPluginModelFacade, getModels(), myIsEnable);
    }
  }

  /** Modifies the state of the plugin list, excluding Ultimate plugins when the Ultimate license is not active. */
  private static void setState(PluginModelFacade pluginModelFacade, Collection<PluginUiModel> models, boolean isEnable) {
    if (models.isEmpty()) return;

    List<PluginId> pluginsRequiringUltimate = UiPluginManager.getInstance()
      .filterPluginsRequiringUltimateButItsDisabled(ContainerUtil.map(models, PluginUiModel::getPluginId));
    var suitableDescriptors = models.stream().
      filter(descriptor -> !pluginsRequiringUltimate.contains(descriptor.getPluginId())).toList();

    if (suitableDescriptors.isEmpty()) return;

    if (isEnable) {
      pluginModelFacade.enable(suitableDescriptors);
    }
    else {
      pluginModelFacade.disable(suitableDescriptors);
    }
  }

  private static boolean containsQuery(PluginUiModel descriptor, String searchQuery) {
    if (descriptor.getName() == null) return false;
    if (StringUtil.containsIgnoreCase(descriptor.getName(), searchQuery)) return true;

    String description = descriptor.getDescription();
    return description != null && StringUtil.containsIgnoreCase(description, searchQuery);
  }

  private static void clearUpdates(@NotNull PluginsGroupComponent panel) {
    for (UIPluginGroup group : panel.getGroups()) {
      for (ListPluginComponent plugin : group.plugins) {
        plugin.setUpdateDescriptor((IdeaPluginDescriptor)null);
      }
    }
  }

  private static void applyUpdates(@NotNull PluginsGroupComponent panel, @NotNull Collection<PluginUiModel> updates) {
    for (PluginUiModel descriptor : updates) {
      for (UIPluginGroup group : panel.getGroups()) {
        ListPluginComponent component = group.findComponent(descriptor.getPluginId());
        if (component != null) {
          component.setUpdateDescriptor(descriptor);
          break;
        }
      }
    }
  }

  private void applyBundledUpdates(@Nullable Collection<? extends PluginUiModel> updates) {
    if (ContainerUtil.isEmpty(updates)) {
      if (myBundledUpdateGroup.ui != null) {
        myInstalledPanel.removeGroup(myBundledUpdateGroup);
        myInstalledPanel.doLayout();
      }
    }
    else if (myBundledUpdateGroup.ui == null) {
      for (PluginUiModel descriptor : updates) {
        for (UIPluginGroup group : myInstalledPanel.getGroups()) {
          ListPluginComponent component = group.findComponent(descriptor.getPluginId());
          if (component != null && component.getPluginModel().isBundled()) {
            myBundledUpdateGroup.addModel(component.getPluginModel());
            break;
          }
        }
      }
      if (!myBundledUpdateGroup.getModels().isEmpty()) {
        myInstalledPanel.addGroup(myBundledUpdateGroup, 0);
        myBundledUpdateGroup.ui.excluded = true;

        for (PluginUiModel descriptor : updates) {
          ListPluginComponent component = myBundledUpdateGroup.ui.findComponent(descriptor.getPluginId());
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
        for (PluginUiModel update : updates) {
          if (plugin.getPluginModel().getPluginId().equals(update.getPluginId())) {
            exist = true;
            break;
          }
        }
        if (!exist) {
          toDelete.add(plugin);
        }
      }

      for (ListPluginComponent component : toDelete) {
        myInstalledPanel.removeFromGroup(myBundledUpdateGroup, component.getPluginModel());
      }

      for (PluginUiModel update : updates) {
        ListPluginComponent exist = myBundledUpdateGroup.ui.findComponent(update.getPluginId());
        if (exist != null) {
          continue;
        }
        for (UIPluginGroup group : myInstalledPanel.getGroups()) {
          if (group == myBundledUpdateGroup.ui) {
            continue;
          }
          ListPluginComponent component = group.findComponent(update.getPluginId());
          if (component != null && component.getPluginModel().isBundled()) {
            myInstalledPanel.addToGroup(myBundledUpdateGroup, component.getPluginModel());
            break;
          }
        }
      }

      if (myBundledUpdateGroup.getModels().isEmpty()) {
        myInstalledPanel.removeGroup(myBundledUpdateGroup);
      }
      else {
        for (PluginUiModel descriptor : updates) {
          ListPluginComponent component = myBundledUpdateGroup.ui.findComponent(descriptor.getPluginId());
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
                                        PluginUiModel model = pluginComponent.getPluginModel();
                                        return String.format("%s (%s)", model.getName(), model.getVersion());
                                      }, "\n");
        CopyPasteManager.getInstance().setContents(new TextTransferable(text));
      }

      @Override
      public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.EDT;
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
      // TODO this function should
      //  - schedule restart in invokeLater with ModalityState.nonModal();
      //  - close settings dialog.
      //  What happens:
      //  - the settings dialog should be displayed in a service coroutine.
      //  - restart awaits completion of all service coroutines.
      //  - calling restart synchronously from this function prevents completion of the service coroutine.
      //  => deadlock IDEA-335883.
      //  IDEA-335883 is currently fixed by showing the dialog outside of the container scope.
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
    PluginManagerConfigurable configurable = new PluginManagerConfigurable();
    ShowSettingsUtil.getInstance().editConfigurable(project,
                                                    configurable,
                                                    () -> configurable.select(pluginIds));
  }

  @ApiStatus.Internal
  public static void showSuggestedPlugins(@Nullable Project project, @Nullable FUSEventSource source) {
    PluginManagerConfigurable configurable = new PluginManagerConfigurable();
    ShowSettingsUtil.getInstance().editConfigurable(project,
                                                    configurable,
                                                    () -> {
                                                      configurable.setInstallSource(source);
                                                      configurable.openMarketplaceTab("/suggested");
                                                    });
  }

  @SuppressWarnings("SameParameterValue")
  private void setInstallSource(@Nullable FUSEventSource source) {
    this.myPluginModelFacade.getModel().setInstallSource(source);
  }

  public static void showPluginConfigurable(@Nullable Component parent,
                                            @Nullable Project project,
                                            @NotNull Collection<PluginId> pluginIds) {
    if (parent != null) {
      PluginManagerConfigurable configurable = new PluginManagerConfigurable();
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
    PluginManagerConfigurable configurable = new PluginManagerConfigurable();
    ShowSettingsUtil.getInstance().editConfigurable(project,
                                                    configurable,
                                                    () -> {
                                                      configurable.myPluginModelFacade.getModel().enable(descriptors);
                                                      configurable.select(descriptors);
                                                    });
  }

  private final class MarketplaceSortByAction extends ToggleAction implements DumbAware {
    private final SortBy myOption;
    private boolean myState;
    private boolean myVisible;

    private MarketplaceSortByAction(@NotNull SortBy option) {
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
      myMarketplaceSortByCallback.accept(this);
    }

    public void setState(@NotNull SearchQueryParser.Marketplace parser) {
      if (myOption == SortBy.RELEVANCE) {
        myState = parser.sortBy == null;
        myVisible = parser.sortBy == null || !parser.tags.isEmpty() || !parser.vendors.isEmpty() || parser.searchQuery != null;
      }
      else {
        myState = parser.sortBy != null && myOption == parser.sortBy;
        myVisible = true;
      }
    }

    public @Nullable String getQuery() {
      if (myOption == SortBy.RELEVANCE) return null;
      return SearchWords.SORT_BY.getValue() + myOption.getQuery();
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

    InstalledSearchOption(Supplier<@Nls String> name) { myPresentableNameSupplier = name; }
  }

  private final class InstalledSearchOptionAction extends ToggleAction implements DumbAware {
    private final InstalledSearchOption myOption;
    private boolean myState;

    private InstalledSearchOptionAction(@NotNull InstalledSearchOption option) {
      super(option.myPresentableNameSupplier);
      getTemplatePresentation().setKeepPopupOnPerform(KeepPopupOnPerform.IfRequested);
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

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.BGT;
    }

    public void setState(@Nullable SearchQueryParser.Installed parser) {
      if (parser == null) {
        myState = false;
        return;
      }

      myState = switch (myOption) {
        case Enabled -> parser.enabled;
        case Disabled -> parser.disabled;
        case Downloaded -> parser.downloaded;
        case Bundled -> parser.bundled;
        case Invalid -> parser.invalid;
        case NeedUpdate -> parser.needUpdate;
      };
    }

    public @NotNull String getQuery() {
      return myOption == InstalledSearchOption.NeedUpdate ? "/outdated" : "/" + StringUtil.decapitalize(myOption.name());
    }
  }

  private static final class GroupByActionGroup extends DefaultActionGroup implements CheckedActionGroup {
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
      Set<PluginUiModel> models = new HashSet<>();
      PluginsGroup group = myPluginModelFacade.getModel().getDownloadedGroup();

      if (group == null || group.ui == null) {
        ApplicationInfoEx appInfo = ApplicationInfoEx.getInstanceEx();

        for (PluginUiModel descriptor : UiPluginManager.getInstance().getPlugins()) {
          if (!appInfo.isEssentialPlugin(descriptor.getPluginId()) &&
              !descriptor.isBundled() && descriptor.isEnabled() != myEnable) {
            models.add(descriptor);
          }
        }
      }
      else {
        for (ListPluginComponent component : group.ui.plugins) {
          PluginUiModel plugin = component.getPluginModel();
          if (myPluginModelFacade.isEnabled(plugin) != myEnable) {
            models.add(plugin);
          }
        }
      }

      setState(myPluginModelFacade, models, myEnable);
    }
  }

  public static @NotNull JComponent createScrollPane(@NotNull PluginsGroupComponent panel, boolean initSelection) {
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
                        @NotNull List<PluginUiModel> customPlugins,
                        @NotNull Predicate<? super PluginsGroup> showAllPredicate,
                        @NotNull Map<PluginId, List<HtmlChunk>> errors) {
    PluginsGroup group = new PluginsGroup(name, type);
    group.setErrors(errors);
    int i = 0;
    for (Iterator<PluginUiModel> iterator = customPlugins.iterator(); iterator.hasNext() && i < ITEMS_PER_GROUP; i++) {
      group.addModel(iterator.next());
    }

    if (showAllPredicate.test(group)) {
      group.rightAction = new LinkLabelButton<>(IdeBundle.message("plugins.configurable.show.all"),
                                                null,
                                                myMarketplaceTab.searchListener,
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
                                          @NotNull Map<PluginId, List<HtmlChunk>> errors) throws IOException {
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
             errors);
  }

  @Override
  public @NotNull String getHelpTopic() {
    return ID;
  }

  @Override
  public void disposeUIResources() {
    InstalledPluginsState pluginsState = InstalledPluginsState.getInstance();
    if (myPluginModelFacade.getModel().toBackground()) {
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
    if (myInstalledSearchPanel != null) {
      myInstalledSearchPanel.dispose();
    }

    myPluginUpdatesService.dispose();
    PluginPriceService.cancel();

    pluginsState.runShutdownCallback();
    pluginsState.resetChangesAppliedWithoutRestart();

    if (myDisposer != null) {
      CoroutineScopeKt.cancel(myCoroutineScope, null);
      myDisposer = null;
    }
  }

  @Override
  public void cancel() {
    myPluginModelFacade.getModel().cancel(myCardPanel, true);
  }

  @Override
  public boolean isModified() {
    if (myPluginsAutoUpdateEnabled != null &&
        UpdateSettings.getInstance().getState().isPluginsAutoUpdateEnabled() != myPluginsAutoUpdateEnabled) {
      return true;
    }
    return myPluginModelFacade.getModel().isModified();
  }

  @Override
  public void apply() throws ConfigurationException {
    if (myPluginsAutoUpdateEnabled != null) {
      UpdateOptions state = UpdateSettings.getInstance().getState();
      if (state.isPluginsAutoUpdateEnabled() != myPluginsAutoUpdateEnabled) {
        state.setPluginsAutoUpdateEnabled(myPluginsAutoUpdateEnabled);
        ApplicationManager.getApplication().getService(PluginAutoUpdateService.class).onSettingsChanged$intellij_platform_ide_impl();
      }
    }

    if (myPluginModelFacade.getModel().apply(myCardPanel)) return;

    if (myPluginModelFacade.getModel().createShutdownCallback) {
      InstalledPluginsState.getInstance().setShutdownCallback(() -> {
        ApplicationManager.getApplication().invokeLater(() -> {
          if (ApplicationManager.getApplication().isExitInProgress()) return; // already shutting down
          shutdownOrRestartApp();
        });
      });
    }
  }

  @Override
  public void reset() {
    if (myPluginsAutoUpdateEnabled != null) {
      myPluginsAutoUpdateEnabled = UpdateSettings.getInstance().getState().isPluginsAutoUpdateEnabled();
    }
    myPluginModelFacade.getModel().clear(myCardPanel);
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

  @Override
  public @Nullable Runnable enableSearch(String option) {
    return enableSearch(option, false);
  }

  public @Nullable Runnable enableSearch(String option, boolean ignoreTagMarketplaceTab) {
    if (myTabHeaderComponent == null) {
      myLaterSearchQuery = option;
      return () -> {
      };
    }

    if (StringUtil.isEmpty(option) && (myTabHeaderComponent.getSelectionTab() == MARKETPLACE_TAB || myInstalledSearchPanel.isEmpty())) {
      return null;
    }

    return () -> {
      boolean marketplace = (!ignoreTagMarketplaceTab && option != null && option.startsWith(SearchWords.TAG.getValue()));
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

  public void openInstalledTab(@NotNull String option) {
    myLaterSearchQuery = option;
    myShowMarketplaceTab = false;
    myForceShowInstalledTabForTag = true;
    if (myTabHeaderComponent != null) {
      updateSelectionTab(INSTALLED_TAB);
    }
  }

  @RequiresEdt
  private void onPluginInstalledFromDisk(@NotNull PluginInstallCallbackData callbackData) {
    myPluginModelFacade.getModel().pluginInstalledFromDisk(callbackData);

    boolean select = myInstalledPanel == null;
    updateSelectionTab(INSTALLED_TAB);

    myInstalledTab.clearSearchPanel("");

    ListPluginComponent component = select ?
                                    findInstalledPluginById(callbackData.getPluginDescriptor().getPluginId()) :
                                    null;
    if (component != null) {
      myInstalledPanel.setSelection(component);
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

  private static class LinkLabelButton<T> extends LinkLabel<T> {
    private LinkLabelButton(@NlsContexts.LinkLabel String text, @Nullable Icon icon) {
      super(text, icon);
    }

    private LinkLabelButton(@NlsContexts.LinkLabel String text, @Nullable Icon icon, @Nullable LinkListener<T> aListener) {
      super(text, icon, aListener);
    }

    private LinkLabelButton(@NlsContexts.LinkLabel String text,
                            @Nullable Icon icon,
                            @Nullable LinkListener<T> aListener,
                            @Nullable T aLinkData) {
      super(text, icon, aListener, aLinkData);
    }

    @Override
    public AccessibleContext getAccessibleContext() {
      if (accessibleContext == null) {
        accessibleContext = new AccessibleLinkLabelButton();
      }
      return accessibleContext;
    }

    protected class AccessibleLinkLabelButton extends AccessibleLinkLabel {
      @Override
      public AccessibleRole getAccessibleRole() {
        return AccessibleRole.PUSH_BUTTON;
      }
    }
  }
}