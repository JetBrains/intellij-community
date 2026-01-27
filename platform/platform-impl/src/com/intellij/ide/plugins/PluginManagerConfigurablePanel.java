// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins;

import com.intellij.execution.process.ProcessIOExecutorService;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.icons.AllIcons;
import com.intellij.ide.CopyProvider;
import com.intellij.ide.DataManager;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.impl.ProjectUtil;
import com.intellij.ide.plugins.certificates.PluginCertificateManager;
import com.intellij.ide.plugins.marketplace.PluginSearchResult;
import com.intellij.ide.plugins.marketplace.ranking.MarketplaceLocalRanker;
import com.intellij.ide.plugins.marketplace.statistics.PluginManagerUsageCollector;
import com.intellij.ide.plugins.newui.*;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.idea.AppMode;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.impl.PresentationFactory;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.DumbAwareToggleAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupListener;
import com.intellij.openapi.ui.popup.LightweightWindowEvent;
import com.intellij.openapi.updateSettings.impl.PluginAutoUpdateListener;
import com.intellij.openapi.updateSettings.impl.UpdateOptions;
import com.intellij.openapi.updateSettings.impl.UpdateSettings;
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.FUSEventSource;
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.PluginsAdvertiserStartupActivityKt;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.WelcomeScreen;
import com.intellij.openapi.wm.impl.welcomeScreen.PluginsTabFactory;
import com.intellij.openapi.wm.impl.welcomeScreen.WelcomeScreenEventCollector;
import com.intellij.ui.ComponentUtil;
import com.intellij.ui.GotItTooltip;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.labels.LinkLabel;
import com.intellij.ui.components.labels.LinkListener;
import com.intellij.ui.popup.ActionPopupOptions;
import com.intellij.ui.popup.PopupFactoryImpl;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.net.HttpProxyConfigurable;
import com.intellij.util.ui.JBDimension;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.StatusText;
import com.intellij.util.ui.TextTransferable;
import kotlinx.coroutines.CoroutineScope;
import kotlinx.coroutines.CoroutineScopeKt;
import kotlinx.coroutines.Dispatchers;
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

import static com.intellij.ide.plugins.PluginManagerConfigurable.*;

@ApiStatus.Internal
public final class PluginManagerConfigurablePanel implements Disposable {

  private static final Logger LOG = Logger.getInstance(PluginManagerConfigurablePanel.class);

  private static final int MARKETPLACE_TAB = 0;
  private static final int INSTALLED_TAB = 1;

  private static final int ITEMS_PER_GROUP = 9;

  private TabbedPaneHeaderComponent myTabHeaderComponent;
  private final CountIcon myInstalledTabHeaderUpdatesCountIcon = new CountIcon();

  private MultiPanel myCardPanel;

  private MarketplacePluginsTab myMarketplaceTab;
  private InstalledPluginsTab myInstalledTab;

  private PluginsGroupComponentWithProgress myMarketplacePanel;

  private Runnable myMarketplaceRunnable;

  private SearchResultPanel myMarketplaceSearchPanel;

  private final CoroutineScope myCoroutineScope;

  private final PluginModelFacade myPluginModelFacade;

  private PluginUpdatesService myPluginUpdatesService;

  private PluginManagerCustomizer myPluginManagerCustomizer;

  private String myLaterSearchQuery;
  private boolean myForceShowInstalledTabForTag = false;
  private boolean myShowMarketplaceTab;

  private Boolean myPluginsAutoUpdateEnabled;
  private volatile Boolean myDisposeStarted = false;

  public PluginManagerConfigurablePanel() {
    myPluginModelFacade = new PluginModelFacade(new MyPluginModel(null));
    myPluginManagerCustomizer = PluginManagerCustomizer.getInstance();
    CoroutineScope parentScope =
      ApplicationManager.getApplication().getService(PluginManagerCoroutineScopeHolder.class).getCoroutineScope();
    CoroutineScope childScope =
      com.intellij.platform.util.coroutines.CoroutineScopeKt.childScope(parentScope, getClass().getName(), Dispatchers.getIO(), true);
    myPluginModelFacade.getModel().setCoroutineScope(childScope);
    myCoroutineScope = childScope;
  }

  public @NotNull JComponent getCenterComponent(@NotNull Configurable.TopComponentController controller) {
    myPluginModelFacade.getModel().setTopController(controller);
    return myTabHeaderComponent;
  }

  public @NotNull JComponent getTopComponent() {
    return getCenterComponent(TopComponentController.EMPTY);
  }

  public void init(@Nullable String searchQuery) {
    myTabHeaderComponent = new TabbedPaneHeaderComponent(createGearActions(), index -> {
      myCardPanel.select(index, true);
      storeSelectionTab(index);

      String query = (index == MARKETPLACE_TAB ? myInstalledTab : myMarketplaceTab).getSearchQuery();
      (index == MARKETPLACE_TAB ? myMarketplaceTab : myInstalledTab).setSearchQuery(query);
    }) {
      @Override
      public void uiDataSnapshot(@NotNull DataSink sink) {
        sink.set(PLUGIN_INSTALL_CALLBACK_DATA_KEY, PluginManagerConfigurablePanel.this::onPluginInstalledFromDisk);
      }
    };
    createGearGotIt();
    myLaterSearchQuery = searchQuery;

    myTabHeaderComponent.addTab(IdeBundle.message("plugin.manager.tab.marketplace"), null);
    myTabHeaderComponent.addTab(IdeBundle.message("plugin.manager.tab.installed"), myInstalledTabHeaderUpdatesCountIcon);

    CustomPluginRepositoryService.getInstance().clearCache();
    myPluginUpdatesService =
      UiPluginManager.getInstance().subscribeToUpdatesCount(myPluginModelFacade.getModel().getSessionId(), updatesCount -> {
        ApplicationManager.getApplication().invokeLater(() -> {
          onPluginUpdatesRecalculation(updatesCount);
        });
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

    if (myPluginManagerCustomizer != null) {
      myPluginManagerCustomizer.initCustomizer(myCardPanel);
    }
  }

  public @NotNull JComponent getComponent() {
    return myCardPanel;
  }


  public boolean isMarketplaceTabShowing() {
    return myTabHeaderComponent.getSelectionTab() == MARKETPLACE_TAB;
  }

  public boolean isInstalledTabShowing() {
    return myTabHeaderComponent.getSelectionTab() == INSTALLED_TAB;
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

      actions.add(new UpdatePluginsAutomaticallyToggleAction());
      actions.addSeparator();
    }
    actions.add(new ManagePluginRepositoriesAction());
    actions.add(new OpenHttpProxyConfigurableAction());
    actions.addSeparator();
    actions.add(new ManagePluginCertificatesAction());

    actions.add(new CustomInstallPluginFromDiskAction());
    if (myPluginManagerCustomizer != null) {
      actions.addAll(myPluginManagerCustomizer.getExtraPluginsActions());
    }
    actions.addSeparator();
    actions.add(new ChangePluginStateAction(false));
    actions.add(new ChangePluginStateAction(true));

    if (ApplicationManager.getApplication().isInternal()) {
      actions.addSeparator();
      actions.add(new ResetConfigurableAction());
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
    GotItTooltip tooltip = new GotItTooltip(title, IdeBundle.message("plugin.manager.plugins.auto.update.description"), this);
    tooltip.withHeader(title);
    tooltip.show((JComponent)myTabHeaderComponent.getComponent(1), (component, balloon) -> {
      return new Point(component.getWidth() / 2, ((JComponent)component).getVisibleRect().height);
    });
  }

  static void showRightBottomPopup(@NotNull Component component, @NotNull @Nls String title, @NotNull ActionGroup group) {
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

    if (myMarketplaceTab != null) {
      myMarketplaceTab.resetCache();
    }

    myPluginUpdatesService.recalculateUpdates();

    if (myMarketplacePanel != null) {
      int selectionTab = myTabHeaderComponent.getSelectionTab();
      if (selectionTab == MARKETPLACE_TAB) {
        myMarketplaceRunnable.run();
      }
      else {
        myMarketplacePanel.setOnBecomingVisibleCallback(myMarketplaceRunnable);
      }
    }
  }

  private void onPluginUpdatesRecalculation(Integer updatesCount) {
    int count = updatesCount == null ? 0 : updatesCount;
    String text = Integer.toString(count);

    String tooltip = PluginUpdatesService.getUpdatesTooltip();
    myTabHeaderComponent.setTabTooltip(INSTALLED_TAB, tooltip);

    myInstalledTab.onPluginUpdatesRecalculation(updatesCount, tooltip);

    myInstalledTabHeaderUpdatesCountIcon.setText(text);
    myTabHeaderComponent.update();
  }

  private static int getStoredSelectionTab() {
    int value = PropertiesComponent.getInstance().getInt(SELECTION_TAB_KEY, MARKETPLACE_TAB);
    return value < MARKETPLACE_TAB || value > INSTALLED_TAB ? MARKETPLACE_TAB : value;
  }

  private static void storeSelectionTab(int value) {
    PropertiesComponent.getInstance().setValue(SELECTION_TAB_KEY, value, MARKETPLACE_TAB);
  }

  private void createMarketplaceTab() {
    myMarketplaceTab = new MarketplacePluginsTab();
  }

  private void createInstalledTab() {
    myInstalledTab = new InstalledPluginsTab(
      myPluginModelFacade,
      myPluginUpdatesService,
      myCoroutineScope,
      query -> myTabHeaderComponent.setSelectionWithEvents(MARKETPLACE_TAB)
    );

    myPluginModelFacade.getModel().setCancelInstallCallback(descriptor -> {
      if (myInstalledTab.getInstalledSearchPanel() == null) {
        return null;
      }

      PluginsGroup group = myInstalledTab.getInstalledSearchPanel().getGroup();

      if (group.ui != null && group.ui.findComponent(descriptor.getPluginId()) != null) {
        myInstalledTab.getInstalledSearchPanel().getPanel().removeFromGroup(group, descriptor);
        group.titleWithCount();
        myInstalledTab.getInstalledSearchPanel().fullRepaint();

        if (group.getModels().isEmpty()) {
          myInstalledTab.getInstalledSearchPanel().removeGroup();
        }
      }
      return null;
    });
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


  /** Modifies the state of the plugin list, excluding Ultimate plugins when the Ultimate license is not active. */
  static void setState(PluginModelFacade pluginModelFacade, Collection<PluginUiModel> models, boolean isEnable) {
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

  static boolean containsQuery(PluginUiModel descriptor, String searchQuery) {
    if (descriptor.getName() == null) return false;
    if (StringUtil.containsIgnoreCase(descriptor.getName(), searchQuery)) return true;

    String description = descriptor.getDescription();
    return description != null && StringUtil.containsIgnoreCase(description, searchQuery);
  }

  static void clearUpdates(@NotNull PluginsGroupComponent panel) {
    for (UIPluginGroup group : panel.getGroups()) {
      for (ListPluginComponent plugin : group.plugins) {
        plugin.setUpdateDescriptor((IdeaPluginDescriptor)null);
      }
    }
  }

  static void applyUpdates(@NotNull PluginsGroupComponent panel, @NotNull Collection<PluginUiModel> updates) {
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

  @SuppressWarnings("SameParameterValue")
  void setInstallSource(@Nullable FUSEventSource source) {
    this.myPluginModelFacade.getModel().setInstallSource(source);
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
    myDisposeStarted = true;
    if (ComponentUtil.getParentOfType(WelcomeScreen.class, myCardPanel) != null && isModified()) {
      scheduleApply();
    }
    InstalledPluginsState pluginsState = InstalledPluginsState.getInstance();
    if (myPluginModelFacade.getModel().toBackground()) {
      pluginsState.clearShutdownCallback();
    }

    if (myMarketplaceTab != null) {
      myMarketplaceTab.dispose();
    }

    if (myInstalledTab != null) {
      myInstalledTab.dispose();
    }

    if (myMarketplacePanel != null) {
      myMarketplacePanel.dispose();
    }
    if (myMarketplaceSearchPanel != null) {
      myMarketplaceSearchPanel.dispose();
    }
    if (myInstalledTab.getInstalledSearchPanel() != null) {
      myInstalledTab.getInstalledSearchPanel().dispose();
    }

    myPluginUpdatesService.dispose();
    PluginPriceService.cancel();

    pluginsState.runShutdownCallback();
    pluginsState.resetChangesAppliedWithoutRestart();

    Disposer.dispose(this);
    CoroutineScopeKt.cancel(myCoroutineScope, null);
  }

  public void cancel() {
    myPluginModelFacade.getModel().cancel(myCardPanel, true);
  }

  public boolean isModified() {
    if (myPluginsAutoUpdateEnabled != null &&
        UpdateSettings.getInstance().getState().isPluginsAutoUpdateEnabled() != myPluginsAutoUpdateEnabled) {
      return true;
    }
    return myPluginModelFacade.getModel().isModified();
  }

  public void scheduleApply() {
    ApplicationManager.getApplication().invokeLater(() -> {
      try {
        apply();
        WelcomeScreenEventCollector.logPluginsModified();
        if (myDisposeStarted) { //To avoid race condition when dispose is called before apply callback gets called
          InstalledPluginsState.getInstance().runShutdownCallback();
        }
      }
      catch (ConfigurationException exception) {
        Logger.getInstance(PluginsTabFactory.class).error(exception);
      }
    }, ModalityState.nonModal());
  }

  public void apply() throws ConfigurationException {
    if (myPluginsAutoUpdateEnabled != null) {
      UpdateOptions state = UpdateSettings.getInstance().getState();
      if (state.isPluginsAutoUpdateEnabled() != myPluginsAutoUpdateEnabled) {
        UiPluginManager.getInstance().setPluginsAutoUpdateEnabled(myPluginsAutoUpdateEnabled);
      }
    }

    myPluginModelFacade.getModel().applyWithCallback(myCardPanel, (installedWithoutRestart) -> {
      if (installedWithoutRestart) return;
      InstalledPluginsState installedPluginsState = InstalledPluginsState.getInstance();
      if (myPluginModelFacade.getModel().createShutdownCallback) {
        installedPluginsState.setShutdownCallback(() -> {
          ApplicationManager.getApplication().invokeLater(() -> {
            if (ApplicationManager.getApplication().isExitInProgress()) return; // already shutting down
            if (myPluginManagerCustomizer != null) {
              myPluginManagerCustomizer.requestRestart(myPluginModelFacade, myTabHeaderComponent);
              return;
            }
            myPluginModelFacade.closeSession();
            shutdownOrRestartApp();
          });
        });
      }

      if (myDisposeStarted) {
        installedPluginsState.runShutdownCallback();
      }
    });
  }

  public void reset() {
    if (myPluginsAutoUpdateEnabled != null) {
      myPluginsAutoUpdateEnabled = UpdateSettings.getInstance().getState().isPluginsAutoUpdateEnabled();
    }
    myPluginModelFacade.getModel().clear(myCardPanel);
  }

  void selectAndEnable(@NotNull Set<? extends IdeaPluginDescriptor> descriptors) {
    myPluginModelFacade.getModel().enable(descriptors);
    select(ContainerUtil.map(descriptors, IdeaPluginDescriptor::getPluginId));
  }

  void select(@NotNull Collection<PluginId> pluginIds) {
    updateSelectionTab(INSTALLED_TAB);

    List<ListPluginComponent> components = new ArrayList<>();

    for (PluginId pluginId : pluginIds) {
      ListPluginComponent component = findInstalledPluginById(pluginId);
      if (component != null) {
        components.add(component);
      }
    }

    if (!components.isEmpty()) {
      myInstalledTab.getInstalledPanel().setSelection(components);
    }
  }

  public @Nullable Runnable enableSearch(String option) {
    return enableSearch(option, false);
  }

  public @Nullable Runnable enableSearch(String option, boolean ignoreTagMarketplaceTab) {
    if (StringUtil.isEmpty(option) &&
        (myTabHeaderComponent.getSelectionTab() == MARKETPLACE_TAB || myInstalledTab.getInstalledSearchPanel().isEmpty())) {
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
    PluginModelAsyncOperationsExecutor.INSTANCE
      .updateErrors(myCoroutineScope, myPluginModelFacade.getModel().getSessionId(), callbackData.getPluginDescriptor().getPluginId(),
                    errors -> {
                      //noinspection unchecked
                      updateAfterPluginInstalledFromDisk(callbackData, (List<HtmlChunk>)errors);
                      return null;
                    });
  }

  private void updateAfterPluginInstalledFromDisk(@NotNull PluginInstallCallbackData callbackData, List<HtmlChunk> errors) {
    myPluginModelFacade.getModel().pluginInstalledFromDisk(callbackData, errors);

    boolean select = myInstalledTab.getInstalledPanel() == null;
    updateSelectionTab(INSTALLED_TAB);

    myInstalledTab.clearSearchPanel("");

    ListPluginComponent component = select ?
                                    findInstalledPluginById(callbackData.getPluginDescriptor().getPluginId()) :
                                    null;
    if (component != null) {
      myInstalledTab.getInstalledPanel().setSelection(component);
    }
  }

  private void updateSelectionTab(int tab) {
    if (myTabHeaderComponent.getSelectionTab() != tab) {
      myTabHeaderComponent.setSelectionWithEvents(tab);
    }
  }

  private @Nullable ListPluginComponent findInstalledPluginById(@NotNull PluginId pluginId) {
    for (UIPluginGroup group : myInstalledTab.getInstalledGroups()) {
      ListPluginComponent component = group.findComponent(pluginId);
      if (component != null) {
        return component;
      }
    }
    return null;
  }

  static class LinkLabelButton<T> extends LinkLabel<T> {
    LinkLabelButton(@NlsContexts.LinkLabel String text, @Nullable Icon icon) {
      super(text, icon);
    }

    LinkLabelButton(@NlsContexts.LinkLabel String text, @Nullable Icon icon, @Nullable LinkListener<T> aListener) {
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

  private class MarketplacePluginsTab extends PluginsTab {
    private final DefaultActionGroup myMarketplaceSortByGroup;
    private LinkComponent myMarketplaceSortByAction;

    private List<String> myTagsSorted;
    private List<String> myVendorsSorted;

    MarketplacePluginsTab() {
      super();
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
        myMarketplaceTab.hideSearchPanel();
      }
      else {
        myMarketplaceTab.showSearchPanel(query);
      }
    }

    @Override
    protected void onSearchReset() {
      PluginManagerUsageCollector.INSTANCE.searchReset();
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

  private class ManagePluginRepositoriesAction extends DumbAwareAction {
    private ManagePluginRepositoriesAction() { super(IdeBundle.message("plugin.manager.repositories")); }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      if (ShowSettingsUtil.getInstance().editConfigurable(myCardPanel, new PluginHostsConfigurable())) {
        if (myPluginManagerCustomizer == null) {
          resetPanels();
        }

        PluginManagerCustomizer customizer = PluginManagerCustomizer.getInstance();
        if (customizer != null) {
          customizer.updateCustomRepositories(UpdateSettings.getInstance().getStoredPluginHosts(), () -> {
            resetPanels();
            return null;
          });
        }
      }
    }
  }

  private class OpenHttpProxyConfigurableAction extends DumbAwareAction {
    private OpenHttpProxyConfigurableAction() { super(IdeBundle.message("button.http.proxy.settings")); }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      if (HttpProxyConfigurable.editConfigurable(myCardPanel)) {
        resetPanels();
      }
    }
  }

  private class ManagePluginCertificatesAction extends DumbAwareAction {
    private ManagePluginCertificatesAction() { super(IdeBundle.message("plugin.manager.custom.certificates")); }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      if (ShowSettingsUtil.getInstance().editConfigurable(myCardPanel, new PluginCertificateManager())) {
        resetPanels();
      }
    }
  }

  private class CustomInstallPluginFromDiskAction extends InstallFromDiskAction {

    private CustomInstallPluginFromDiskAction() {
      super(PluginManagerConfigurablePanel.this.myPluginModelFacade.getModel(),
            PluginManagerConfigurablePanel.this.myPluginModelFacade.getModel(), PluginManagerConfigurablePanel.this.myCardPanel);
    }

    @RequiresEdt
    @Override
    protected void onPluginInstalledFromDisk(@NotNull PluginInstallCallbackData callbackData,
                                             @Nullable Project project) {
      if (myPluginManagerCustomizer != null) {
        myPluginManagerCustomizer.updateAfterModification(() -> {
          PluginManagerConfigurablePanel.this.onPluginInstalledFromDisk(callbackData);
          return null;
        });
        return;
      }
      PluginManagerConfigurablePanel.this.onPluginInstalledFromDisk(callbackData);
    }
  }

  private class ResetConfigurableAction extends DumbAwareAction {
    private ResetConfigurableAction() { super(IdeBundle.message("plugin.manager.refresh")); }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      resetPanels();
    }
  }

  private class UpdatePluginsAutomaticallyToggleAction extends DumbAwareToggleAction {
    private UpdatePluginsAutomaticallyToggleAction() { super(IdeBundle.message("updates.plugins.autoupdate.settings.action")); }

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
      PluginModelAsyncOperationsExecutor.INSTANCE.switchPlugins(myCoroutineScope, myPluginModelFacade, myEnable, models -> {
        //noinspection unchecked
        setState(myPluginModelFacade, (List<PluginUiModel>)models, myEnable);
        return null;
      });
    }
  }
}
