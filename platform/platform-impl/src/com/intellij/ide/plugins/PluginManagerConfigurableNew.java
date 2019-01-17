// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins;

import com.google.gson.stream.JsonToken;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.ide.CopyProvider;
import com.intellij.ide.DataManager;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.plugins.newui.*;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.idea.IdeaApplication;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.application.impl.ApplicationInfoImpl;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.updateSettings.impl.PluginDownloader;
import com.intellij.openapi.updateSettings.impl.UpdateSettings;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.*;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextField;
import com.intellij.ui.components.labels.LinkLabel;
import com.intellij.ui.components.labels.LinkListener;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.util.*;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.io.HttpRequests;
import com.intellij.util.io.URLUtil;
import com.intellij.util.net.HttpConfigurable;
import com.intellij.util.ui.*;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.io.JsonReaderEx;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.*;
import java.awt.event.*;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URLConnection;
import java.net.UnknownHostException;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;

/**
 * @author Alexander Lobas
 */
public class PluginManagerConfigurableNew
  implements SearchableConfigurable, Configurable.NoScroll, Configurable.NoMargin, Configurable.TopComponentProvider {
  public static final String ID = "preferences.pluginManager";

  private static final String SELECTION_TAB_KEY = "PluginConfigurable.selectionTab";
  private static final int TRENDING_TAB = 0;
  private static final int INSTALLED_TAB = 1;
  private static final int UPDATES_TAB = 2;
  private static final int TRENDING_SEARCH_TAB = 3;
  private static final int INSTALLED_SEARCH_TAB = 4;
  private static final int UPDATES_SEARCH_TAB = 5;

  private static final int ITEMS_PER_GROUP = 9;

  private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("MMM dd, yyyy");
  private static final DecimalFormat K_FORMAT = new DecimalFormat("###.#K");
  private static final DecimalFormat M_FORMAT = new DecimalFormat("###.#M");

  @SuppressWarnings("UseJBColor")
  public static final Color MAIN_BG_COLOR =
    JBColor.namedColor("Plugins.background", new JBColor(() -> JBColor.isBright() ? UIUtil.getListBackground() : new Color(0x313335)));

  private static final Color SEARCH_BG_COLOR = JBColor.namedColor("Plugins.SearchField.background", MAIN_BG_COLOR);

  private static final Color SEARCH_FIELD_BORDER_COLOR =
    JBColor.namedColor("Plugins.SearchField.borderColor", new JBColor(0xC5C5C5, 0x515151));

  private static final SimpleTextAttributes GRAY_ATTRIBUTES =
    new SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, ListPluginComponent.DisabledColor);

  private final TagBuilder myTagBuilder;

  private LinkListener<IdeaPluginDescriptor> myNameListener;
  private LinkListener<String> mySearchListener;

  private CardLayoutPanel<Object, Object, JComponent> myCardPanel;
  private TabHeaderComponent myTabHeaderComponent;
  private CountTabName myUpdatesTabName;
  private TopComponentController myTopController;

  private final PluginSearchTextField mySearchTextField;
  private final Alarm mySearchUpdateAlarm = new Alarm();

  private PluginsGroupComponentWithProgress myTrendingPanel;
  private PluginsGroupComponent myInstalledPanel;
  private PluginsGroupComponentWithProgress myUpdatesPanel;

  private Runnable myTrendingRunnable;
  private Runnable myUpdatesRunnable;

  private SearchResultPanel myTrendingSearchPanel;
  private SearchResultPanel myInstalledSearchPanel;
  private SearchResultPanel myUpdatesSearchPanel;
  private SearchResultPanel myCurrentSearchPanel;

  private final MyPluginModel myPluginsModel = new MyPluginModel() {
    @Override
    public List<IdeaPluginDescriptor> getAllRepoPlugins() {
      return getPluginRepositories();
    }
  };

  private Runnable myShutdownCallback;

  private PluginUpdatesService myPluginUpdatesService;

  private List<IdeaPluginDescriptor> myAllRepositoriesList;
  private Map<String, IdeaPluginDescriptor> myAllRepositoriesMap;
  private Map<String, List<IdeaPluginDescriptor>> myCustomRepositoriesMap;
  private final Object myRepositoriesLock = new Object();
  private List<String> myAllTagSorted;

  private boolean myIgnoreFocusFromBackButton;

  public PluginManagerConfigurableNew() {
    myTagBuilder = new TagBuilder() {
      @NotNull
      @Override
      public TagComponent createTagComponent(@NotNull String tag) {
        return installTiny(new TagComponent(tag));
      }
    };

    mySearchTextField = new PluginSearchTextField() {
      @Override
      protected boolean preprocessEventForTextField(KeyEvent event) {
        int keyCode = event.getKeyCode();
        int id = event.getID();

        if (keyCode == KeyEvent.VK_ENTER || event.getKeyChar() == '\n') {
          if (id == KeyEvent.KEY_PRESSED &&
              (myCurrentSearchPanel.controller == null || !myCurrentSearchPanel.controller.handleEnter(event))) {
            String text = mySearchTextField.getText();
            if (!text.isEmpty()) {
              if (myCurrentSearchPanel.controller != null) {
                myCurrentSearchPanel.controller.hidePopup();
              }
              showSearchPanel(text);
            }
          }
          return true;
        }
        if ((keyCode == KeyEvent.VK_DOWN || keyCode == KeyEvent.VK_UP) && id == KeyEvent.KEY_PRESSED &&
            myCurrentSearchPanel.controller != null && myCurrentSearchPanel.controller.handleUpDown(event)) {
          return true;
        }
        return super.preprocessEventForTextField(event);
      }

      @Override
      protected boolean toClearTextOnEscape() {
        new AnAction() {
          {
            setEnabledInModalContext(true);
          }

          @Override
          public void update(@NotNull AnActionEvent e) {
            e.getPresentation().setEnabled(!getText().isEmpty());
          }

          @Override
          public void actionPerformed(@NotNull AnActionEvent e) {
            if (myCurrentSearchPanel.controller != null && myCurrentSearchPanel.controller.isPopupShow()) {
              myCurrentSearchPanel.controller.hidePopup();
            }
            else {
              setText("");
            }
          }
        }.registerCustomShortcutSet(CommonShortcuts.ESCAPE, this);
        return false;
      }

      @Override
      protected void onFieldCleared() {
        hideSearchPanel();
      }

      @Override
      protected void showCompletionPopup() {
        if (myCurrentSearchPanel.controller != null && !myCurrentSearchPanel.controller.isPopupShow()) {
          showSearchPopup();
        }
      }
    };
    mySearchTextField.setBorder(JBUI.Borders.customLine(SEARCH_FIELD_BORDER_COLOR));

    JBTextField editor = mySearchTextField.getTextEditor();
    editor.putClientProperty("JTextField.Search.Gap", JBUI.scale(6));
    editor.putClientProperty("JTextField.Search.GapEmptyText", JBUI.scale(-1));
    editor.putClientProperty("StatusVisibleFunction", (BooleanFunction<JBTextField>)field -> field.getText().isEmpty());
    editor.setBorder(JBUI.Borders.empty(0, 6));
    editor.setOpaque(true);
    editor.setBackground(SEARCH_BG_COLOR);
  }

  @NotNull
  @Override
  public String getId() {
    return ID;
  }

  @Nls
  @Override
  public String getDisplayName() {
    return IdeBundle.message("title.plugins");
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return mySearchTextField.getTextEditor();
  }

  @Nullable
  @Override
  public JComponent createComponent() {
    JPanel panel = new JPanel(new BorderLayout()) {
      @Override
      public void addNotify() {
        super.addNotify();
        EventHandler.addGlobalAction(mySearchTextField, new CustomShortcutSet(KeyStroke.getKeyStroke("meta alt F")), () -> {
          IdeFocusManager.getGlobalInstance()
            .doWhenFocusSettlesDown(() -> IdeFocusManager.getGlobalInstance().requestFocus(mySearchTextField, true));
          if (myCurrentSearchPanel.controller != null) {
            showSearchPopup();
          }
        });
      }
    };
    panel.setMinimumSize(new JBDimension(580, 380));

    DefaultActionGroup actions = new DefaultActionGroup();
    actions.add(new DumbAwareAction("Manage Plugin Repositories...") {
      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        if (ShowSettingsUtil.getInstance().editConfigurable(panel, new PluginHostsConfigurable())) {
          resetTrendingAndUpdatesPanels();
        }
      }
    });
    actions.add(new DumbAwareAction(IdeBundle.message("button.http.proxy.settings")) {
      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        if (HttpConfigurable.editConfigurable(panel)) {
          resetTrendingAndUpdatesPanels();
        }
      }
    });
    actions.addSeparator();
    actions.add(new DumbAwareAction("Install Plugin from Disk...") {
      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        InstalledPluginsManagerMain.chooseAndInstall(myPluginsModel, pair -> {
          myPluginsModel.appendOrUpdateDescriptor(pair.second);

          boolean select = myInstalledPanel == null;

          if (myTabHeaderComponent.getSelectionTab() != INSTALLED_TAB) {
            myTabHeaderComponent.setSelectionWithEvents(INSTALLED_TAB);
          }

          hideSearchPanel();
          mySearchTextField.setTextIgnoreEvents("");

          if (select) {
            for (UIPluginGroup group : myInstalledPanel.getGroups()) {
              CellPluginComponent component = group.findComponent(pair.second);
              if (component != null) {
                myInstalledPanel.setSelection(component);
                break;
              }
            }
          }
        }, panel);
      }
    });

    panel.add(mySearchTextField, BorderLayout.NORTH);

    myNameListener = (label, descriptor) -> {
      int detailBackTabIndex = -1;

      if (label == null) {
        if (myPluginsModel.detailPanel != null) {
          detailBackTabIndex = myPluginsModel.detailPanel.backTabIndex;
        }
        removeDetailsPanel();
      }

      assert myPluginsModel.detailPanel == null;

      int currentTab = detailBackTabIndex == -1 ? myTabHeaderComponent.getSelectionTab() : detailBackTabIndex;

      JButton backButton = new BackButton();
      backButton.addActionListener(event -> {
        removeDetailsPanel();
        myIgnoreFocusFromBackButton = true;
        myCardPanel.select(myCurrentSearchPanel.isEmpty() ? currentTab : myCurrentSearchPanel.tabIndex, true);
        storeSelectionTab(currentTab);
        myTabHeaderComponent.setSelection(currentTab);
      });

      myCardPanel.select(Pair.create(descriptor, label != null && currentTab == UPDATES_TAB), true);
      myPluginsModel.detailPanel.backTabIndex = currentTab;

      NonOpaquePanel buttonPanel = new NonOpaquePanel(backButton) {
        @Override
        public int getBaseline(int width, int height) {
          return backButton.getBaseline(width, height);
        }
      };
      buttonPanel.setBorder(JBUI.Borders.empty(0, 3));
      myTopController.setLeftComponent(buttonPanel);
      myTabHeaderComponent.clearSelection();
    };

    mySearchListener = (_0, query) -> {
      removeDetailsPanel();
      mySearchTextField.setTextIgnoreEvents(query);
      IdeFocusManager.getGlobalInstance()
        .doWhenFocusSettlesDown(() -> IdeFocusManager.getGlobalInstance().requestFocus(mySearchTextField, true));
      myCurrentSearchPanel.setEmpty();
      showSearchPanel(query);
    };

    mySearchTextField.getTextEditor().addFocusListener(new FocusListener() {
      @Override
      public void focusGained(FocusEvent e) {
        if (myIgnoreFocusFromBackButton) {
          myIgnoreFocusFromBackButton = false;
          return;
        }
        if (myCurrentSearchPanel.controller != null) {
          showSearchPopup();
        }
      }

      @Override
      public void focusLost(FocusEvent e) {
        if (myCurrentSearchPanel.controller != null) {
          myCurrentSearchPanel.controller.hidePopup();
        }
      }
    });

    mySearchTextField.getTextEditor().getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(@NotNull DocumentEvent e) {
        if (!mySearchTextField.isSkipDocumentEvents()) {
          mySearchUpdateAlarm.cancelAllRequests();
          mySearchUpdateAlarm.addRequest(this::searchOnTheFly, 100, ModalityState.stateForComponent(mySearchTextField));
        }
      }

      private void searchOnTheFly() {
        String text = mySearchTextField.getText();
        if (StringUtil.isEmptyOrSpaces(text)) {
          hideSearchPanel();
        }
        else if (myCurrentSearchPanel.controller == null) {
          showSearchPanel(text);
        }
        else {
          myCurrentSearchPanel.controller.handleShowPopup();
        }
      }
    });

    myCardPanel = new CardLayoutPanel<Object, Object, JComponent>() {
      @Override
      public ActionCallback select(Object key, boolean now) {
        ActionCallback callback = super.select(key, now);
        callback.doWhenDone(() -> {
          for (Component component : getComponents()) {
            if (component.isVisible() && component instanceof JScrollPane) {
              Component view = ((JScrollPane)component).getViewport().getView();
              if (view instanceof PluginsGroupComponentWithProgress) {
                view.setVisible(true);
              }
            }
          }

          panel.doLayout();
          panel.revalidate();
          panel.repaint();
        });
        return callback;
      }

      @Override
      protected Object prepare(Object key) {
        return key;
      }

      @Override
      protected JComponent create(Object key) {
        if (key instanceof Integer) {
          Integer index = (Integer)key;
          if (index == TRENDING_TAB) {
            return createTrendingPanel();
          }
          if (index == INSTALLED_TAB) {
            return createInstalledPanel();
          }
          if (index == UPDATES_TAB) {
            return createUpdatesPanel();
          }
          if (index == TRENDING_SEARCH_TAB) {
            return myTrendingSearchPanel.createScrollPane();
          }
          if (index == INSTALLED_SEARCH_TAB) {
            return myInstalledSearchPanel.createScrollPane();
          }
          if (index == UPDATES_SEARCH_TAB) {
            return myUpdatesSearchPanel.createScrollPane();
          }
          throw new RuntimeException("Create card unknown KEY index: " + key);
        }

        //noinspection unchecked
        return createDetailsPanel((Pair<IdeaPluginDescriptor, Boolean>)key);
      }
    };
    panel.add(myCardPanel);

    myTabHeaderComponent = new TabHeaderComponent(actions, index -> {
      removeDetailsPanel();
      myIgnoreFocusFromBackButton = false;
      myCardPanel.select(index, true);
      storeSelectionTab(index);
      updateSearchForSelectedTab(index);
      if (!myCurrentSearchPanel.isEmpty()) {
        myCardPanel.select(myCurrentSearchPanel.tabIndex, true);
      }
    });

    myTabHeaderComponent.addTab("Marketplace");
    myTabHeaderComponent.addTab("Installed");
    myTabHeaderComponent.addTab(myUpdatesTabName = new CountTabName(myTabHeaderComponent, "Updates"));

    myPluginUpdatesService =
      PluginUpdatesService.connectConfigurable(countValue -> myUpdatesTabName.setCount(countValue == null ? 0 : countValue));
    myPluginsModel.setPluginUpdatesService(myPluginUpdatesService);

    createSearchPanels();

    int selectionTab = getStoredSelectionTab();
    myTabHeaderComponent.setSelection(selectionTab);
    myCardPanel.select(selectionTab, true);
    updateSearchForSelectedTab(selectionTab);

    panel.addComponentListener(new ComponentAdapter() {
      boolean myStoreHosts;
      List<String> myHosts;

      @Override
      public void componentShown(ComponentEvent e) {
        myStoreHosts = true;
        if (myHosts != null) {
          List<String> oldHosts = myHosts;
          List<String> newHosts = UpdateSettings.getInstance().getPluginHosts();
          myHosts = null;

          if (oldHosts.size() != newHosts.size()) {
            resetTrendingAndUpdatesPanels();
            return;
          }
          for (String host : oldHosts) {
            if (!newHosts.contains(host)) {
              resetTrendingAndUpdatesPanels();
              return;
            }
          }
        }
      }

      @Override
      public void componentHidden(ComponentEvent e) {
        if (myStoreHosts) {
          myHosts = UpdateSettings.getInstance().getPluginHosts();
        }
      }
    });

    return panel;
  }

  private void showSearchPopup() {
    if (StringUtil.isEmptyOrSpaces(mySearchTextField.getText())) {
      myCurrentSearchPanel.controller.showAttributesPopup(null, 0);
    }
    else {
      myCurrentSearchPanel.controller.handleShowPopup();
    }
  }

  @Nullable
  @Override
  public Runnable enableSearch(String option) {
    if (StringUtil.isEmpty(option) && myCurrentSearchPanel.isEmpty()) {
      return null;
    }

    return () -> {
      if (myTabHeaderComponent.getSelectionTab() != INSTALLED_TAB) {
        myTabHeaderComponent.setSelectionWithEvents(INSTALLED_TAB);
      }

      hideSearchPanel();
      mySearchTextField.setTextIgnoreEvents(option);

      if (!StringUtil.isEmpty(option)) {
        showSearchPanel(option);
      }
    };
  }

  public void select(@NotNull IdeaPluginDescriptor... descriptors) {
    myIgnoreFocusFromBackButton = true;

    if (myTabHeaderComponent.getSelectionTab() != INSTALLED_TAB) {
      myTabHeaderComponent.setSelectionWithEvents(INSTALLED_TAB);
    }

    if (descriptors.length == 0) {
      return;
    }

    List<CellPluginComponent> components = new ArrayList<>();

    for (IdeaPluginDescriptor descriptor : descriptors) {
      for (UIPluginGroup group : myInstalledPanel.getGroups()) {
        CellPluginComponent component = group.findComponent(descriptor);
        if (component != null) {
          components.add(component);
          break;
        }
      }
    }

    if (!components.isEmpty()) {
      myInstalledPanel.setSelection(components);
    }
  }

  @NotNull
  public MyPluginModel getPluginsModel() {
    return myPluginsModel;
  }

  private void updateSearchForSelectedTab(int index) {
    String text;
    String historyPropertyName;
    SearchResultPanel searchPanel;
    if (index == TRENDING_TAB) {
      text = "Search plugins in marketplace";
      if (!UpdateSettings.getInstance().getPluginHosts().isEmpty()) {
        text += " and custom repositories";
      }
      searchPanel = myTrendingSearchPanel;
      historyPropertyName = "TrendingPluginsSearchHistory";
    }
    else if (index == INSTALLED_TAB) {
      text = "Search installed plugins";
      searchPanel = myInstalledSearchPanel;
      historyPropertyName = "InstalledPluginsSearchHistory";
    }
    else {
      text = "Search available updates";
      searchPanel = myUpdatesSearchPanel;
      historyPropertyName = "UpdatePluginsSearchHistory";
    }

    StatusText emptyText = mySearchTextField.getTextEditor().getEmptyText();
    emptyText.clear();
    emptyText.appendText(text, new SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, CellPluginComponent.GRAY_COLOR));

    myCurrentSearchPanel = searchPanel;
    mySearchTextField.addCurrentTextToHistory();
    mySearchTextField.setHistoryPropertyName(historyPropertyName);
    mySearchTextField.setTextIgnoreEvents(searchPanel.getQuery());
  }

  private void showSearchPanel(@NotNull String query) {
    if (myCurrentSearchPanel.isEmpty()) {
      myCardPanel.select(myCurrentSearchPanel.tabIndex, true);
    }
    myCurrentSearchPanel.setQuery(query);
  }

  private void hideSearchPanel() {
    if (!myCurrentSearchPanel.isEmpty()) {
      myCardPanel.select(myCurrentSearchPanel.backTabIndex, true);
      myCurrentSearchPanel.setQuery("");
    }
    if (myCurrentSearchPanel.controller != null) {
      myCurrentSearchPanel.controller.hidePopup();
    }
  }

  private void removeDetailsPanel() {
    if (myPluginsModel.detailPanel != null) {
      mySearchTextField.setVisible(true);
      myPluginsModel.detailPanel.close();
      myPluginsModel.detailPanel = null;
      myTopController.setLeftComponent(null);
      myCardPanel.remove(myCardPanel.getComponentCount() - 1);
    }
  }

  private static int getStoredSelectionTab() {
    int value = PropertiesComponent.getInstance().getInt(SELECTION_TAB_KEY, TRENDING_TAB);
    return value >= TRENDING_TAB && value <= UPDATES_TAB ? value : TRENDING_TAB;
  }

  private static void storeSelectionTab(int value) {
    if (value >= TRENDING_TAB && value <= UPDATES_TAB) {
      PropertiesComponent.getInstance().setValue(SELECTION_TAB_KEY, value, TRENDING_TAB);
    }
  }

  @Override
  public void disposeUIResources() {
    myPluginsModel.toBackground();

    Disposer.dispose(mySearchUpdateAlarm);
    myTrendingSearchPanel.dispose();

    myPluginUpdatesService.dispose();

    if (myTrendingPanel != null) {
      myTrendingPanel.dispose();
    }
    if (myUpdatesPanel != null) {
      myUpdatesPanel.dispose();
    }

    if (myShutdownCallback != null) {
      myShutdownCallback.run();
      myShutdownCallback = null;
    }
  }

  @Override
  public void apply() throws ConfigurationException {
    Map<PluginId, Boolean> enabledMap = myPluginsModel.getEnabledMap();
    List<String> dependencies = new ArrayList<>();

    for (Entry<PluginId, Set<PluginId>> entry : myPluginsModel.getDependentToRequiredListMap().entrySet()) {
      PluginId id = entry.getKey();

      if (enabledMap.get(id) == null) {
        continue;
      }

      for (PluginId dependId : entry.getValue()) {
        if (!PluginManagerCore.isModuleDependency(dependId)) {
          IdeaPluginDescriptor descriptor = PluginManager.getPlugin(id);
          if (!(descriptor instanceof IdeaPluginDescriptorImpl) || !((IdeaPluginDescriptorImpl)descriptor).isDeleted()) {
            dependencies.add("\"" + (descriptor == null ? id.getIdString() : descriptor.getName()) + "\"");
          }
          break;
        }
      }
    }

    if (!dependencies.isEmpty()) {
      throw new ConfigurationException("<html><body style=\"padding: 5px;\">Unable to apply changes: plugin" +
                                       (dependencies.size() == 1 ? " " : "s ") +
                                       StringUtil.join(dependencies, ", ") +
                                       " won't be able to load.</body></html>");
    }

    int rowCount = myPluginsModel.getRowCount();
    for (int i = 0; i < rowCount; i++) {
      IdeaPluginDescriptor descriptor = myPluginsModel.getObjectAt(i);
      descriptor.setEnabled(myPluginsModel.isEnabled(descriptor.getPluginId()));
    }

    List<String> disableIds = new ArrayList<>();
    for (Entry<PluginId, Boolean> entry : enabledMap.entrySet()) {
      Boolean enabled = entry.getValue();
      if (enabled != null && !enabled) {
        disableIds.add(entry.getKey().getIdString());
      }
    }

    try {
      PluginManagerCore.saveDisabledPlugins(disableIds, false);
    }
    catch (IOException e) {
      PluginManagerMain.LOG.error(e);
    }

    if (myShutdownCallback == null && myPluginsModel.createShutdownCallback) {
      myShutdownCallback = () -> ApplicationManager.getApplication().invokeLater(
        () -> PluginManagerConfigurable.shutdownOrRestartApp(IdeBundle.message("update.notifications.title")));
    }
  }

  @Override
  public boolean isModified() {
    if (myPluginsModel.needRestart) {
      return true;
    }

    List<String> disabledPlugins = PluginManagerCore.getDisabledPlugins();
    int rowCount = myPluginsModel.getRowCount();

    for (int i = 0; i < rowCount; i++) {
      IdeaPluginDescriptor descriptor = myPluginsModel.getObjectAt(i);
      boolean enabledInTable = myPluginsModel.isEnabled(descriptor);

      if (descriptor.isEnabled() != enabledInTable) {
        if (enabledInTable && !disabledPlugins.contains(descriptor.getPluginId().getIdString())) {
          continue; // was disabled automatically on startup
        }
        return true;
      }
    }

    for (Entry<PluginId, Boolean> entry : myPluginsModel.getEnabledMap().entrySet()) {
      Boolean enabled = entry.getValue();
      if (enabled != null && !enabled && !disabledPlugins.contains(entry.getKey().getIdString())) {
        return true;
      }
    }

    return false;
  }

  @NotNull
  @Override
  public Component getCenterComponent(@NotNull TopComponentController controller) {
    myTopController = controller;
    myPluginsModel.setTopController(controller);
    return myTabHeaderComponent;
  }

  @NotNull
  private JComponent createTrendingPanel() {
    myTrendingPanel =
      new PluginsGroupComponentWithProgress(new PluginsGridLayout(), new ScrollEventHandler(), myNameListener, mySearchListener,
                                            descriptor -> new GridCellPluginComponent(myPluginsModel, descriptor, myTagBuilder));

    Runnable runnable = () -> {
      List<PluginsGroup> groups = new ArrayList<>();

      try {
        Pair<Map<String, IdeaPluginDescriptor>, Map<String, List<IdeaPluginDescriptor>>> pair = loadPluginRepositories();
        Map<String, IdeaPluginDescriptor> allRepositoriesMap = pair.first;
        Map<String, List<IdeaPluginDescriptor>> customRepositoriesMap = pair.second;

        try {
          addGroup(groups, allRepositoriesMap, "Featured", "is_featured_search=true", "sortBy:featured");
          addGroup(groups, allRepositoriesMap, "New and Updated", "orderBy=update+date", "sortBy:updated");
          addGroup(groups, allRepositoriesMap, "Top Downloads", "orderBy=downloads", "sortBy:downloads");
          addGroup(groups, allRepositoriesMap, "Top Rated", "orderBy=rating", "sortBy:rating");
        }
        catch (UnknownHostException e) {
          PluginManagerMain.LOG
            .info("Main plugin repository '" + e.getMessage() + "' is not available. Please check your network settings.");
        }

        for (String host : UpdateSettings.getInstance().getPluginHosts()) {
          List<IdeaPluginDescriptor> allDescriptors = customRepositoriesMap.get(host);
          if (allDescriptors != null) {
            addGroup(groups, "Repository: " + host, "repository:\"" + host + "\"", descriptors -> {
              int allSize = allDescriptors.size();
              descriptors.addAll(ContainerUtil.getFirstItems(allDescriptors, ITEMS_PER_GROUP));
              PluginsGroup.sortByName(descriptors);
              return allSize > ITEMS_PER_GROUP;
            });
          }
        }
      }
      catch (IOException e) {
        PluginManagerMain.LOG.info(e);
      }
      finally {
        ApplicationManager.getApplication().invokeLater(() -> {
          myTrendingPanel.stopLoading();
          PluginLogo.startBatchMode();

          for (PluginsGroup group : groups) {
            myTrendingPanel.addGroup(group);
          }

          PluginLogo.endBatchMode();
          myTrendingPanel.doLayout();
          myTrendingPanel.initialSelection();
        }, ModalityState.any());
      }
    };

    myTrendingRunnable = () -> {
      myTrendingPanel.clear();
      myTrendingPanel.startLoading();
      ApplicationManager.getApplication().executeOnPooledThread(runnable);
    };

    myTrendingPanel.getEmptyText().setText("Marketplace plugins are not loaded.")
      .appendSecondaryText("Check the internet connection and ", StatusText.DEFAULT_ATTRIBUTES, null)
      .appendSecondaryText("refresh", SimpleTextAttributes.LINK_PLAIN_ATTRIBUTES, e -> myTrendingRunnable.run());

    ApplicationManager.getApplication().executeOnPooledThread(runnable);
    return createScrollPane(myTrendingPanel, false);
  }

  @NotNull
  private JComponent createInstalledPanel() {
    PluginsGroupComponent panel =
      new PluginsGroupComponent(new PluginsListLayout(), new MultiSelectionEventHandler(), myNameListener, mySearchListener,
                                descriptor -> new ListPluginComponent(myPluginsModel, descriptor, false));
    registerCopyProvider(panel);
    PluginLogo.startBatchMode();

    PluginsGroup installing = new PluginsGroup("Installing");
    installing.descriptors.addAll(MyPluginModel.getInstallingPlugins());
    if (!installing.descriptors.isEmpty()) {
      installing.sortByName();
      installing.titleWithCount();
      panel.addGroup(installing);
    }

    PluginsGroup downloaded = new PluginsGroup("Downloaded");
    PluginsGroup bundled = new PluginsGroup("Bundled");

    downloaded.descriptors.addAll(InstalledPluginsState.getInstance().getInstalledPlugins());

    ApplicationInfoEx appInfo = ApplicationInfoEx.getInstanceEx();
    int bundledEnabled = 0;
    int downloadedEnabled = 0;

    for (IdeaPluginDescriptor descriptor : PluginManagerCore.getPlugins()) {
      if (!appInfo.isEssentialPlugin(descriptor.getPluginId().getIdString())) {
        if (descriptor.isBundled()) {
          bundled.descriptors.add(descriptor);
          if (descriptor.isEnabled()) {
            bundledEnabled++;
          }
        }
        else {
          downloaded.descriptors.add(descriptor);
          if (descriptor.isEnabled()) {
            downloadedEnabled++;
          }
        }
      }
    }

    if (!downloaded.descriptors.isEmpty()) {
      downloaded.sortByName();
      downloaded.titleWithCount(downloadedEnabled);
      panel.addGroup(downloaded);
      myPluginsModel.addEnabledGroup(downloaded);
    }

    myPluginsModel.setDownloadedGroup(panel, downloaded, installing);

    bundled.sortByName();
    bundled.titleWithCount(bundledEnabled);
    panel.addGroup(bundled);
    myPluginsModel.addEnabledGroup(bundled);

    myInstalledPanel = panel;

    myPluginUpdatesService.connectInstalled(updates -> {
      if (ContainerUtil.isEmpty(updates)) {
        for (UIPluginGroup group : myInstalledPanel.getGroups()) {
          for (CellPluginComponent plugin : group.plugins) {
            ((ListPluginComponent)plugin).setUpdateDescriptor(null);
          }
        }
      }
      else {
        for (PluginDownloader downloader : updates) {
          IdeaPluginDescriptor descriptor = downloader.getDescriptor();
          for (UIPluginGroup group : myInstalledPanel.getGroups()) {
            CellPluginComponent component = group.findComponent(descriptor);
            if (component != null) {
              ((ListPluginComponent)component).setUpdateDescriptor(descriptor);
              break;
            }
          }
        }
      }
    });

    PluginLogo.endBatchMode();
    return createScrollPane(panel, true);
  }

  @NotNull
  private JComponent createUpdatesPanel() {
    myUpdatesPanel =
      new PluginsGroupComponentWithProgress(new PluginsListLayout(), new MultiSelectionEventHandler(), myNameListener, mySearchListener,
                                            descriptor -> new ListPluginComponent(myPluginsModel, descriptor, true));
    registerCopyProvider(myUpdatesPanel);

    myUpdatesRunnable = () -> {
      myUpdatesPanel.clear();
      myUpdatesPanel.startLoading();

      myPluginUpdatesService.calculateUpdates(updates -> {
        myUpdatesPanel.stopLoading();

        if (!ContainerUtil.isEmpty(updates)) {
          PluginsGroup group = new PluginsGroup("Available Updates") {
            @Override
            public void titleWithCount() {
              int count = 0;
              for (CellPluginComponent component : ui.plugins) {
                if (((ListPluginComponent)component).myUpdateButton != null) {
                  count++;
                }
              }

              title = myTitlePrefix + " (" + count + ")";
              updateTitle();
              rightAction.setVisible(count > 0);
            }
          };

          group.rightAction = new LinkLabel<>("Update All", null);
          group.rightAction.setListener(new LinkListener<Object>() {
            @Override
            public void linkSelected(LinkLabel aSource, Object aLinkData) {
              for (CellPluginComponent component : group.ui.plugins) {
                ((ListPluginComponent)component).updatePlugin();
              }
            }
          }, null);

          for (PluginDownloader toUpdateDownloader : updates) {
            group.descriptors.add(toUpdateDownloader.getDescriptor());
          }

          PluginLogo.startBatchMode();
          group.sortByName();
          myUpdatesPanel.addGroup(group);
          group.titleWithCount();
          PluginLogo.endBatchMode();

          myPluginsModel.setUpdateGroup(group);
        }

        myUpdatesPanel.doLayout();
        myUpdatesPanel.initialSelection();
      });
    };

    myUpdatesPanel.getEmptyText().setText("No updates available.")
      .appendSecondaryText("Check new updates", SimpleTextAttributes.LINK_PLAIN_ATTRIBUTES, e -> myUpdatesRunnable.run());

    myUpdatesRunnable.run();
    return createScrollPane(myUpdatesPanel, false);
  }

  private void createSearchPanels() {
    SearchPopupController trendingController = new SearchPopupController(mySearchTextField) {
      @NotNull
      @Override
      protected List<String> getAttributes() {
        List<String> attributes = new ArrayList<>();
        attributes.add("tag:");
        if (!UpdateSettings.getInstance().getPluginHosts().isEmpty()) {
          attributes.add("repository:");
        }
        attributes.add("sortBy:");
        return attributes;
      }

      @Nullable
      @Override
      protected List<String> getValues(@NotNull String attribute) {
        switch (attribute) {
          case "tag:":
            if (ContainerUtil.isEmpty(myAllTagSorted)) {
              Set<String> allTags = new HashSet<>();
              for (IdeaPluginDescriptor descriptor : getPluginRepositories()) {
                if (descriptor instanceof PluginNode) {
                  List<String> tags = ((PluginNode)descriptor).getTags();
                  if (!ContainerUtil.isEmpty(tags)) {
                    allTags.addAll(tags);
                  }
                }
              }
              myAllTagSorted = ContainerUtil.sorted(allTags, String::compareToIgnoreCase);
            }
            return myAllTagSorted;
          case "repository:":
            return UpdateSettings.getInstance().getPluginHosts();
          case "sortBy:":
            return ContainerUtil.list("downloads", "name", "rating", "featured", "updated");
        }
        return null;
      }

      @Override
      protected void showPopupForQuery() {
        String query = mySearchTextField.getText().trim();
        if (mySearchTextField.getTextEditor().getCaretPosition() < query.length()) {
          hidePopup();
          return;
        }

        List<IdeaPluginDescriptor> result = loadSuggestPlugins(query);
        if (result.isEmpty()) {
          hidePopup();
          return;
        }

        boolean async = myPopup != null;
        boolean update = myPopup != null && myPopup.type == SearchPopup.Type.SearchQuery && myPopup.isValid();
        if (update) {
          myPopup.model.replaceAll(result);
        }
        else {
          createPopup(SearchPopup.Type.SearchQuery, new CollectionListModel<>(result), 0);
        }

        myPopup.data = query;

        if (update) {
          myPopup.update();
          return;
        }

        Consumer<IdeaPluginDescriptor> callback = descriptor -> {
          hidePopup();
          myNameListener.linkSelected(null, descriptor);
        };

        ColoredListCellRenderer renderer = new ColoredListCellRenderer() {
          @Override
          protected void customizeCellRenderer(@NotNull JList list, Object value, int index, boolean selected, boolean hasFocus) {
            IdeaPluginDescriptor descriptor = (IdeaPluginDescriptor)value;

            String splitter = myPopup == null ? null : (String)myPopup.data;
            for (String partName : SearchQueryParser.split(descriptor.getName(), splitter)) {
              append(partName, partName.equalsIgnoreCase(splitter)
                               ? SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES
                               : SimpleTextAttributes.REGULAR_ATTRIBUTES);
            }

            if (isJBPlugin(descriptor)) {
              append(" by JetBrains", GRAY_ATTRIBUTES);
            }
            else {
              String vendor = descriptor.getVendor();
              if (!StringUtil.isEmptyOrSpaces(vendor)) {
                append(" by " + StringUtil.shortenPathWithEllipsis(vendor, 50), GRAY_ATTRIBUTES);
              }
            }
          }
        };

        myPopup.createAndShow(callback, renderer, async);
      }

      @NotNull
      private List<IdeaPluginDescriptor> loadSuggestPlugins(@NotNull String query) {
        Set<IdeaPluginDescriptor> result = new LinkedHashSet<>();
        try {
          ApplicationManager.getApplication().executeOnPooledThread(() -> {
            try {
              Pair<Map<String, IdeaPluginDescriptor>, Map<String, List<IdeaPluginDescriptor>>> p = loadPluginRepositories();
              Map<String, IdeaPluginDescriptor> allRepositoriesMap = p.first;
              Map<String, List<IdeaPluginDescriptor>> customRepositoriesMap = p.second;

              if (query.length() > 1) {
                try {
                  for (String pluginId : requestToPluginRepository(createSearchSuggestUrl(query), forceHttps())) {
                    IdeaPluginDescriptor descriptor = allRepositoriesMap.get(pluginId);
                    if (descriptor != null) {
                      result.add(descriptor);
                    }
                  }
                }
                catch (IOException ignore) {
                }
              }

              for (List<IdeaPluginDescriptor> descriptors : customRepositoriesMap.values()) {
                for (IdeaPluginDescriptor descriptor : descriptors) {
                  if (StringUtil.containsIgnoreCase(descriptor.getName(), query)) {
                    result.add(descriptor);
                  }
                }
              }
            }
            catch (IOException e) {
              PluginManagerMain.LOG.info(e);
            }
          }).get(300, TimeUnit.MILLISECONDS);
        }
        catch (Exception ignore) {
        }
        return ContainerUtil.newArrayList(result);
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
    myTrendingSearchPanel =
      new SearchResultPanel(trendingController, createSearchPanelComponentWithProgress(), TRENDING_SEARCH_TAB, TRENDING_TAB) {
        @Override
        protected void handleQuery(@NotNull String query, @NotNull PluginsGroup result) {
          try {
            Pair<Map<String, IdeaPluginDescriptor>, Map<String, List<IdeaPluginDescriptor>>> p = loadPluginRepositories();
            Map<String, IdeaPluginDescriptor> allRepositoriesMap = p.first;
            Map<String, List<IdeaPluginDescriptor>> customRepositoriesMap = p.second;

            SearchQueryParser.Trending parser = new SearchQueryParser.Trending(query);

            if (!parser.repositories.isEmpty()) {
              for (String repository : parser.repositories) {
                List<IdeaPluginDescriptor> descriptors = customRepositoriesMap.get(repository);
                if (descriptors == null) {
                  continue;
                }
                if (parser.searchQuery == null) {
                  result.descriptors.addAll(descriptors);
                }
                else {
                  for (IdeaPluginDescriptor descriptor : descriptors) {
                    if (StringUtil.containsIgnoreCase(descriptor.getName(), parser.searchQuery)) {
                      result.descriptors.add(descriptor);
                    }
                  }
                }
              }
              result.sortByName();
              return;
            }

            for (String pluginId : requestToPluginRepository(createSearchUrl(parser.getUrlQuery(), 10000), forceHttps())) {
              IdeaPluginDescriptor descriptor = allRepositoriesMap.get(pluginId);
              if (descriptor != null) {
                result.descriptors.add(descriptor);
              }
            }

            if (parser.searchQuery != null) {
              for (List<IdeaPluginDescriptor> descriptors : customRepositoriesMap.values()) {
                for (IdeaPluginDescriptor descriptor : descriptors) {
                  if (StringUtil.containsIgnoreCase(descriptor.getName(), parser.searchQuery)) {
                    result.descriptors.add(descriptor);
                  }
                }
              }
            }
          }
          catch (IOException e) {
            PluginManagerMain.LOG.info(e);

            ApplicationManager.getApplication().invokeLater(() -> myPanel.getEmptyText().setText("Search result are not loaded.")
              .appendSecondaryText("Check the internet connection.", StatusText.DEFAULT_ATTRIBUTES, null), ModalityState.any());
          }
        }
      };

    SearchPopupController installedController = new SearchPopupController(mySearchTextField) {
      @NotNull
      @Override
      protected List<String> getAttributes() {
        return ContainerUtil.list("#disabled", "#enabled", "#bundled", "#custom", "#inactive", "#invalid", "#outdated", "#uninstalled");
      }

      @Nullable
      @Override
      protected List<String> getValues(@NotNull String attribute) {
        return null;
      }

      @Override
      protected void handleAppendToQuery() {
        showPopupForQuery();
      }

      @Override
      protected void handleAppendAttributeValue() {
        showPopupForQuery();
      }

      @Override
      protected void showPopupForQuery() {
        showSearchPanel(mySearchTextField.getText());
      }
    };
    myInstalledSearchPanel =
      new SearchResultPanel(installedController, createLocalSearchPanelComponent(false), INSTALLED_SEARCH_TAB, INSTALLED_TAB) {
        @Override
        protected void handleQuery(@NotNull String query, @NotNull PluginsGroup result) {
          InstalledPluginsState state = InstalledPluginsState.getInstance();
          SearchQueryParser.Installed parser = new SearchQueryParser.Installed(query);

          for (UIPluginGroup uiGroup : myInstalledPanel.getGroups()) {
            for (CellPluginComponent plugin : uiGroup.plugins) {
              if (parser.attributes) {
                if (parser.enabled != null && parser.enabled != myPluginsModel.isEnabled(plugin.myPlugin)) {
                  continue;
                }
                if (parser.bundled != null && parser.bundled != plugin.myPlugin.isBundled()) {
                  continue;
                }
                if (parser.invalid != null && parser.invalid != myPluginsModel.hasErrors(plugin.myPlugin)) {
                  continue;
                }
                if (parser.deleted != null) {
                  if (plugin.myPlugin instanceof IdeaPluginDescriptorImpl) {
                    if (parser.deleted != ((IdeaPluginDescriptorImpl)plugin.myPlugin).isDeleted()) {
                      continue;
                    }
                  }
                  else if (parser.deleted) {
                    continue;
                  }
                }
                PluginId pluginId = plugin.myPlugin.getPluginId();
                if (parser.needUpdate != null && parser.needUpdate != state.hasNewerVersion(pluginId)) {
                  continue;
                }
                if (parser.needRestart != null) {
                  if (parser.needRestart != (state.wasInstalled(pluginId) || state.wasUpdated(pluginId))) {
                    continue;
                  }
                }
              }
              if (parser.searchQuery != null && !StringUtil.containsIgnoreCase(plugin.myPlugin.getName(), parser.searchQuery)) {
                continue;
              }
              result.descriptors.add(plugin.myPlugin);
            }
          }
        }
      };

    myUpdatesSearchPanel = new SearchResultPanel(null, createLocalSearchPanelComponent(true), UPDATES_SEARCH_TAB, UPDATES_TAB) {
      @Override
      protected void handleQuery(@NotNull String query, @NotNull PluginsGroup result) {
        for (UIPluginGroup uiGroup : myUpdatesPanel.getGroups()) {
          for (CellPluginComponent plugin : uiGroup.plugins) {
            if (StringUtil.containsIgnoreCase(plugin.myPlugin.getName(), query)) {
              result.descriptors.add(plugin.myPlugin);
            }
          }
        }
      }
    };
  }

  @NotNull
  private JComponent createDetailsPanel(@NotNull Pair<IdeaPluginDescriptor, Boolean> data) {
    myPluginsModel.detailPanel = new DetailsPagePluginComponent(myPluginsModel, myTagBuilder, mySearchListener, data.first, data.second);
    mySearchTextField.setVisible(false);
    return myPluginsModel.detailPanel;
  }

  private void resetTrendingAndUpdatesPanels() {
    synchronized (myRepositoriesLock) {
      myAllRepositoriesList = null;
      myAllRepositoriesMap = null;
      myCustomRepositoriesMap = null;
    }

    myPluginUpdatesService.recalculateUpdates();

    if (myTrendingPanel == null && myUpdatesPanel == null) {
      return;
    }

    int selectionTab = myTabHeaderComponent.getSelectionTab();
    if (selectionTab == TRENDING_TAB) {
      if (myUpdatesPanel != null) {
        myUpdatesPanel.setVisibleRunnable(myUpdatesRunnable);
      }
      myTrendingRunnable.run();
    }
    else if (selectionTab == UPDATES_TAB) {
      if (myTrendingPanel != null) {
        myTrendingPanel.setVisibleRunnable(myTrendingRunnable);
      }
      myUpdatesRunnable.run();
    }
    else {
      if (myTrendingPanel != null) {
        myTrendingPanel.setVisibleRunnable(myTrendingRunnable);
      }
      if (myUpdatesPanel != null) {
        myUpdatesPanel.setVisibleRunnable(myUpdatesRunnable);
      }
    }
  }

  @NotNull
  private static JComponent createScrollPane(@NotNull PluginsGroupComponent panel, boolean initSelection) {
    JBScrollPane pane = new JBScrollPane(panel);
    pane.setBorder(JBUI.Borders.empty());
    if (initSelection) {
      panel.initialSelection();
    }
    return pane;
  }

  @NotNull
  private Pair<Map<String, IdeaPluginDescriptor>, Map<String, List<IdeaPluginDescriptor>>> loadPluginRepositories() throws IOException {
    synchronized (myRepositoriesLock) {
      if (myAllRepositoriesMap != null) {
        return Pair.create(myAllRepositoriesMap, myCustomRepositoriesMap);
      }
    }

    List<IdeaPluginDescriptor> list = new ArrayList<>();
    Map<String, IdeaPluginDescriptor> map = new HashMap<>();
    Map<String, List<IdeaPluginDescriptor>> custom = new HashMap<>();
    IOException exception = null;

    for (String host : RepositoryHelper.getPluginHosts()) {
      try {
        List<IdeaPluginDescriptor> descriptors = RepositoryHelper.loadPlugins(host, null);
        if (host != null) {
          custom.put(host, descriptors);
        }
        for (IdeaPluginDescriptor plugin : descriptors) {
          String id = plugin.getPluginId().getIdString();
          if (!map.containsKey(id)) {
            list.add(plugin);
            map.put(id, plugin);
          }
        }
      }
      catch (UnknownHostException e) {
        PluginManagerMain.LOG.info("Main plugin repository '" + e.getMessage() + "' is not available. Please check your network settings.");
      }
      catch (IOException e) {
        if (host == null) {
          exception = e;
        }
        else {
          PluginManagerMain.LOG.info(host, e);
        }
      }
    }

    if (exception != null) {
      throw exception;
    }

    ApplicationManager.getApplication().invokeLater(() -> {
      InstalledPluginsState state = InstalledPluginsState.getInstance();
      for (IdeaPluginDescriptor descriptor : list) {
        state.onDescriptorDownload(descriptor);
      }
    });

    synchronized (myRepositoriesLock) {
      if (myAllRepositoriesList == null) {
        myAllRepositoriesList = list;
        myAllRepositoriesMap = map;
        myCustomRepositoriesMap = custom;
      }
      return Pair.create(myAllRepositoriesMap, myCustomRepositoriesMap);
    }
  }

  private void addGroup(@NotNull List<PluginsGroup> groups,
                        @NotNull String name,
                        @NotNull String showAllQuery,
                        @NotNull ThrowableNotNullFunction<List<IdeaPluginDescriptor>, Boolean, IOException> function) throws IOException {
    PluginsGroup group = new PluginsGroup(name);

    if (Boolean.TRUE.equals(function.fun(group.descriptors))) {
      //noinspection unchecked
      group.rightAction = new LinkLabel("Show All", null, mySearchListener, showAllQuery);
    }

    if (!group.descriptors.isEmpty()) {
      groups.add(group);
    }
  }

  private void addGroup(@NotNull List<PluginsGroup> groups,
                        @NotNull Map<String, IdeaPluginDescriptor> allRepositoriesMap,
                        @NotNull String name,
                        @NotNull String query,
                        @NotNull String showAllQuery) throws IOException {
    addGroup(groups, name, showAllQuery, descriptors -> loadPlugins(descriptors, allRepositoriesMap, query));
  }

  private static boolean loadPlugins(@NotNull List<? super IdeaPluginDescriptor> descriptors,
                                     @NotNull Map<String, IdeaPluginDescriptor> allDescriptors,
                                     @NotNull String query) throws IOException {
    boolean forceHttps = forceHttps();
    Url baseUrl = createSearchUrl(query, ITEMS_PER_GROUP);
    Url offsetUrl = baseUrl;
    Map<String, String> offsetParameters = new HashMap<>();
    int offset = 0;

    while (true) {
      List<String> pluginIds = requestToPluginRepository(offsetUrl, forceHttps);
      if (pluginIds.isEmpty()) {
        return false;
      }

      for (String pluginId : pluginIds) {
        IdeaPluginDescriptor descriptor = allDescriptors.get(pluginId);
        if (descriptor != null) {
          descriptors.add(descriptor);
          if (descriptors.size() == ITEMS_PER_GROUP) {
            return true;
          }
        }
      }

      offset += pluginIds.size();
      offsetParameters.put("offset", Integer.toString(offset));
      offsetUrl = baseUrl.addParameters(offsetParameters);
    }
  }

  @NotNull
  private static List<String> requestToPluginRepository(@NotNull Url url, boolean forceHttps) throws IOException {
    List<String> ids = new ArrayList<>();

    HttpRequests.request(url).forceHttps(forceHttps).throwStatusCodeException(false).productNameAsUserAgent().connect(request -> {
      URLConnection connection = request.getConnection();
      if (connection instanceof HttpURLConnection && ((HttpURLConnection)connection).getResponseCode() != HttpURLConnection.HTTP_OK) {
        return null;
      }

      try (JsonReaderEx json = new JsonReaderEx(FileUtil.loadTextAndClose(request.getReader()))) {
        if (json.peek() == JsonToken.BEGIN_OBJECT) {
          json.beginObject();
          json.nextName(); // query
          json.nextString(); // query value
          json.nextName(); // suggestions
        }
        json.beginArray();
        while (json.hasNext()) {
          ids.add(json.nextString());
        }
      }

      return null;
    });

    return ids;
  }

  @NotNull
  private static Url createSearchUrl(@NotNull String query, int count) {
    ApplicationInfoEx instance = ApplicationInfoImpl.getShadowInstance();
    return Urls.newFromEncoded(instance.getPluginManagerUrl() + "/api/search?" + query +
                               "&build=" + URLUtil.encodeURIComponent(instance.getApiVersion()) +
                               "&max=" + count);
  }

  @NotNull
  private static Url createSearchSuggestUrl(@NotNull String query) {
    ApplicationInfoEx instance = ApplicationInfoImpl.getShadowInstance();
    return Urls.newFromEncoded(instance.getPluginManagerUrl() + "/api/searchSuggest?term=" + URLUtil.encodeURIComponent(query) +
                               "&productCode=" + URLUtil.encodeURIComponent(instance.getBuild().getProductCode()));
  }

  public static boolean forceHttps() {
    return IdeaApplication.isLoaded() && !ApplicationManager.getApplication().isDisposed() &&
           UpdateSettings.getInstance().canUseSecureConnection();
  }

  @NotNull
  private List<IdeaPluginDescriptor> getPluginRepositories() {
    synchronized (myRepositoriesLock) {
      if (myAllRepositoriesList != null) {
        return myAllRepositoriesList;
      }
    }
    try {
      List<IdeaPluginDescriptor> list = RepositoryHelper.loadCachedPlugins();
      if (list != null) {
        return list;
      }
    }
    catch (IOException e) {
      PluginManagerMain.LOG.info(e);
    }
    return Collections.emptyList();
  }

  @NotNull
  public static String getErrorMessage(@NotNull InstalledPluginsTableModel pluginsModel,
                                       @NotNull PluginDescriptor pluginDescriptor,
                                       @NotNull Ref<? super Boolean> enableAction) {
    String message;

    Set<PluginId> requiredPlugins = pluginsModel.getRequiredPlugins(pluginDescriptor.getPluginId());
    if (ContainerUtil.isEmpty(requiredPlugins)) {
      message = "Incompatible with the current " + ApplicationNamesInfo.getInstance().getFullProductName() + " version.";
    }
    else if (requiredPlugins.contains(PluginId.getId("com.intellij.modules.ultimate"))) {
      message = "The plugin requires IntelliJ IDEA Ultimate.";
    }
    else {
      String deps = StringUtil.join(requiredPlugins, id -> {
        IdeaPluginDescriptor plugin = PluginManager.getPlugin(id);
        if (plugin == null && PluginManagerCore.isModuleDependency(id)) {
          for (IdeaPluginDescriptor descriptor : PluginManagerCore.getPlugins()) {
            if (descriptor instanceof IdeaPluginDescriptorImpl) {
              if (((IdeaPluginDescriptorImpl)descriptor).getModules().contains(id.getIdString())) {
                plugin = descriptor;
                break;
              }
            }
          }
        }
        return plugin != null ? plugin.getName() : id.getIdString();
      }, ", ");

      message = IdeBundle.message("new.plugin.manager.incompatible.deps.tooltip", requiredPlugins.size(), deps);
      enableAction.set(Boolean.TRUE);
    }

    return message;
  }

  @NotNull
  public static List<String> getTags(@NotNull IdeaPluginDescriptor plugin) {
    List<String> tags = null;

    if (plugin instanceof PluginNode) {
      tags = ((PluginNode)plugin).getTags();
    }
    if (ContainerUtil.isEmpty(tags)) {
      return Collections.emptyList();
    }

    int eap = tags.indexOf("EAP");
    if (eap > 0) {
      tags = new ArrayList<>(tags);
      tags.remove(eap);
      tags.add(0, "EAP");
    }

    return tags;
  }

  @Nullable
  public static synchronized String getDownloads(@NotNull IdeaPluginDescriptor plugin) {
    String downloads = ((PluginNode)plugin).getDownloads();
    if (!StringUtil.isEmptyOrSpaces(downloads)) {
      try {
        Long value = Long.valueOf(downloads);
        if (value > 1000) {
          return value < 1000000 ? K_FORMAT.format(value / 1000D) : M_FORMAT.format(value / 1000000D);
        }
        return value.toString();
      }
      catch (NumberFormatException ignore) {
      }
    }

    return null;
  }

  @Nullable
  public static synchronized String getLastUpdatedDate(@NotNull IdeaPluginDescriptor plugin) {
    long date = ((PluginNode)plugin).getDate();
    return date > 0 && date != Long.MAX_VALUE ? DATE_FORMAT.format(new Date(date)) : null;
  }

  @Nullable
  public static String getRating(@NotNull IdeaPluginDescriptor plugin) {
    String rating = ((PluginNode)plugin).getRating();
    if (rating != null) {
      try {
        if (Double.valueOf(rating) > 0) {
          return StringUtil.trimEnd(rating, ".0");
        }
      }
      catch (NumberFormatException ignore) {
      }
    }

    return null;
  }

  private static class CountTabName implements Computable<String> {
    private final TabHeaderComponent myTabComponent;
    private final String myBaseName;
    private int myCount = -1;

    CountTabName(@NotNull TabHeaderComponent component, @NotNull String baseName) {
      myTabComponent = component;
      myBaseName = baseName;
    }

    public void setCount(int count) {
      if (myCount != count) {
        myCount = count;
        myTabComponent.update();
      }
    }

    @Override
    public String compute() {
      return myCount == -1 ? myBaseName : myBaseName + " (" + myCount + ")";
    }
  }

  private static void registerCopyProvider(@NotNull PluginsGroupComponent component) {
    CopyProvider copyProvider = new CopyProvider() {
      @Override
      public void performCopy(@NotNull DataContext dataContext) {
        StringBuilder result = new StringBuilder();
        for (CellPluginComponent pluginComponent : component.getSelection()) {
          result.append(pluginComponent.myPlugin.getName()).append(" (").append(pluginComponent.myPlugin.getVersion()).append(")\n");
        }
        CopyPasteManager.getInstance().setContents(new TextTransferable(result.substring(0, result.length() - 1)));
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

  public static int getParentWidth(@NotNull Container parent) {
    int width = parent.getWidth();

    if (width > 0) {
      Container container = parent.getParent();
      int parentWidth = container.getWidth();

      if (container instanceof JViewport && parentWidth < width) {
        width = parentWidth;
      }
    }

    return width;
  }

  @NotNull
  public static <T extends Component> T installTiny(@NotNull T component) {
    return SystemInfo.isMac ? RelativeFont.TINY.install(component) : component;
  }

  public static int offset5() {
    return JBUI.scale(5);
  }

  public static boolean isJBPlugin(@NotNull IdeaPluginDescriptor plugin) {
    return plugin.isBundled() || PluginManagerMain.isDevelopedByJetBrains(plugin);
  }

  @Nullable
  public static String getShortDescription(@NotNull IdeaPluginDescriptor plugin, boolean shortSize) {
    return PluginSiteUtils.preparePluginDescription(plugin.getDescription(), shortSize);
  }

  @NotNull
  private PluginsGroupComponent createLocalSearchPanelComponent(boolean pluginForUpdate) {
    PluginsGroupComponent component =
      new PluginsGroupComponent(new PluginsListLayout(), new MultiSelectionEventHandler(), myNameListener, mySearchListener,
                                descriptor -> new ListPluginComponent(myPluginsModel, descriptor, pluginForUpdate));
    registerCopyProvider(component);
    return component;
  }

  @NotNull
  private PluginsGroupComponent createSearchPanelComponentWithProgress() {
    return new PluginsGroupComponentWithProgress(new PluginsGridLayout(), new ScrollEventHandler(), myNameListener, mySearchListener,
                                                 descriptor -> new GridCellPluginComponent(myPluginsModel, descriptor, myTagBuilder));
  }
}