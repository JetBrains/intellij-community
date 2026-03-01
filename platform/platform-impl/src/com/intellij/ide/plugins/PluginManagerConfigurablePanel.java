// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins;

import com.intellij.ide.CopyProvider;
import com.intellij.ide.DataManager;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.plugins.certificates.PluginCertificateManager;
import com.intellij.ide.plugins.marketplace.statistics.PluginManagerUsageCollector;
import com.intellij.ide.plugins.newui.ListPluginComponent;
import com.intellij.ide.plugins.newui.MyPluginModel;
import com.intellij.ide.plugins.newui.PluginManagerCustomizer;
import com.intellij.ide.plugins.newui.PluginModelAsyncOperationsExecutor;
import com.intellij.ide.plugins.newui.PluginModelFacade;
import com.intellij.ide.plugins.newui.PluginPriceService;
import com.intellij.ide.plugins.newui.PluginUiModel;
import com.intellij.ide.plugins.newui.PluginUpdatesService;
import com.intellij.ide.plugins.newui.PluginsGroup;
import com.intellij.ide.plugins.newui.PluginsGroupComponent;
import com.intellij.ide.plugins.newui.PluginsTab;
import com.intellij.ide.plugins.newui.SearchWords;
import com.intellij.ide.plugins.newui.TabbedPaneHeaderComponent;
import com.intellij.ide.plugins.newui.UIPluginGroup;
import com.intellij.ide.plugins.newui.UiPluginManager;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.idea.AppMode;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CheckedActionGroup;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DataSink;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.actionSystem.impl.PresentationFactory;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.ShowSettingsUtil;
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
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.WelcomeScreen;
import com.intellij.openapi.wm.impl.welcomeScreen.PluginsTabFactory;
import com.intellij.openapi.wm.impl.welcomeScreen.WelcomeScreenEventCollector;
import com.intellij.ui.ComponentUtil;
import com.intellij.ui.GotItTooltip;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.labels.LinkLabel;
import com.intellij.ui.components.labels.LinkListener;
import com.intellij.ui.popup.ActionPopupOptions;
import com.intellij.ui.popup.PopupFactoryImpl;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.net.HttpProxyConfigurable;
import com.intellij.util.ui.JBDimension;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.TextTransferable;
import kotlinx.coroutines.CoroutineScope;
import kotlinx.coroutines.CoroutineScopeKt;
import kotlinx.coroutines.Dispatchers;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.accessibility.AccessibleContext;
import javax.accessibility.AccessibleRole;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.ScrollPaneConstants;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Point;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import static com.intellij.ide.plugins.PluginManagerConfigurable.PLUGIN_INSTALL_CALLBACK_DATA_KEY;
import static com.intellij.ide.plugins.PluginManagerConfigurable.SELECTION_TAB_KEY;
import static com.intellij.ide.plugins.PluginManagerConfigurable.TopComponentController;
import static com.intellij.ide.plugins.PluginManagerConfigurable.shutdownOrRestartApp;

@ApiStatus.Internal
public final class PluginManagerConfigurablePanel implements Disposable {
  private static final int MARKETPLACE_TAB = 0;
  private static final int INSTALLED_TAB = 1;

  private final CoroutineScope myCoroutineScope;

  private final PluginModelFacade myPluginModelFacade;
  private PluginUpdatesService myPluginUpdatesService;
  private final @Nullable PluginManagerCustomizer myPluginManagerCustomizer;

  private TabbedPaneHeaderComponent myTabHeaderComponent;
  private final CountIcon myInstalledTabHeaderUpdatesCountIcon = new CountIcon();

  private MarketplacePluginsTab myMarketplaceTab;
  private InstalledPluginsTab myInstalledTab;
  private MultiPanel myCardPanel;

  private String myLaterSearchQuery;
  private boolean myForceShowInstalledTabForTag = false;
  private boolean myShowMarketplaceTab;

  private Boolean myPluginsAutoUpdateEnabled;
  private volatile Boolean myDisposeStarted = false;
  private final Object myCallbackLock = new Object();
  private boolean myShutdownCallbackExecuted = false;

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

    if (myMarketplaceTab != null) {
      myMarketplaceTab.onPanelReset(myTabHeaderComponent.getSelectionTab() == MARKETPLACE_TAB);
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
    myMarketplaceTab = new MarketplacePluginsTab(myPluginModelFacade, myCoroutineScope, myPluginManagerCustomizer);
  }

  private void createInstalledTab() {
    myInstalledTab = new InstalledPluginsTab(
      myPluginModelFacade,
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

  @Override
  public void dispose() {
    synchronized (myCallbackLock) {
      myDisposeStarted = true;
    }

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

    if (myMarketplaceTab != null) {
      myMarketplaceTab.dispose();
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
        synchronized (myCallbackLock) {
          if (myDisposeStarted && !myShutdownCallbackExecuted) {
            InstalledPluginsState.getInstance().runShutdownCallback();
          }
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

      synchronized (myCallbackLock) {
        if (myShutdownCallbackExecuted) {
          return;
        }

        if (myPluginModelFacade.getModel().createShutdownCallback) {
          installedPluginsState.setShutdownCallback(() -> {
            synchronized (myCallbackLock) {
              if (myShutdownCallbackExecuted) {
                return;
              }
              myShutdownCallbackExecuted = true;
            }

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
      }

      synchronized (myCallbackLock) {
        if (myDisposeStarted && !myShutdownCallbackExecuted) {
          installedPluginsState.runShutdownCallback();
        }
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
        (myTabHeaderComponent.getSelectionTab() == MARKETPLACE_TAB || myInstalledTab.getInstalledSearchPanel().isQueryEmpty())) {
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

    LinkLabelButton(@NlsContexts.LinkLabel String text,
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
