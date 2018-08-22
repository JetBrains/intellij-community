// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins;

import com.intellij.icons.AllIcons;
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
import com.intellij.openapi.updateSettings.impl.UpdateChecker;
import com.intellij.openapi.updateSettings.impl.UpdateSettings;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.*;
import com.intellij.ui.components.JBOptionButton;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextField;
import com.intellij.ui.components.labels.LinkLabel;
import com.intellij.ui.components.labels.LinkListener;
import com.intellij.util.*;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.io.HttpRequests;
import com.intellij.util.net.HttpConfigurable;
import com.intellij.util.ui.*;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.io.JsonReaderEx;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.event.DocumentEvent;
import java.awt.*;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URLConnection;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.*;
import java.util.Map.Entry;

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

  private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("MMM dd, yyyy");
  private static final DecimalFormat K_FORMAT = new DecimalFormat("###.#K");
  private static final DecimalFormat M_FORMAT = new DecimalFormat("###.#M");

  @SuppressWarnings("UseJBColor")
  public static final Color MAIN_BG_COLOR = new JBColor(() -> JBColor.isBright() ? UIUtil.getListBackground() : new Color(0x313335));

  private final TagBuilder myTagBuilder;

  private LinkListener<IdeaPluginDescriptor> myNameListener;
  private LinkListener<String> mySearchListener;

  private CardLayoutPanel<Object, Object, JComponent> myCardPanel;
  private TabHeaderComponent myTabHeaderComponent;
  private CountTabName myUpdatesTabName;
  private TopComponentController myTopController;

  private final PluginSearchTextField mySearchTextField;
  private final Alarm mySearchUpdateAlarm = new Alarm();

  private PluginsGroupComponent myInstalledPanel;
  private PluginsGroupComponent myUpdatesPanel;

  private SearchResultPanel myTrendingSearchPanel;
  private SearchResultPanel myInstalledSearchPanel;
  private SearchResultPanel myUpdatesSearchPanel;
  private SearchResultPanel myCurrentSearchPanel;

  private final MyPluginModel myPluginsModel = new MyPluginModel();

  private Runnable myShutdownCallback;

  private List<IdeaPluginDescriptor> myJBRepositoryList;
  private Map<String, IdeaPluginDescriptor> myJBRepositoryMap;
  private final Object myJBRepositoryLock = new Object();
  private List<String> myAllTagSorted;

  public PluginManagerConfigurableNew() {
    myTagBuilder = new TagBuilder() {
      @NotNull
      @Override
      public TagComponent createTagComponent(@NotNull String tag) {
        Color color;
        String tooltip = null;
        if ("EAP".equals(tag)) {
          color = new JBColor(0xF2D2CF, 0xF2D2CF);
          tooltip = "The EAP version does not guarantee the stability\nand availability of the plugin.";
        }
        else {
          color = new JBColor(0xEAEAEC, 0x4D4D4D);
        }

        return installTiny(new TagComponent(tag, tooltip, color));
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
            if (myCurrentSearchPanel.controller != null) {
              myCurrentSearchPanel.controller.hidePopup();
            }
            showSearchPanel(mySearchTextField.getText());
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
    };
    mySearchTextField.setBorder(JBUI.Borders.customLine(new JBColor(0xC5C5C5, 0x515151)));

    JBTextField editor = mySearchTextField.getTextEditor();
    editor.putClientProperty("JTextField.Search.Gap", JBUI.scale(8 - 25));
    editor.putClientProperty("JTextField.Search.GapEmptyText", JBUI.scale(8));
    editor.putClientProperty("StatusVisibleFunction", (BooleanFunction<JBTextField>)field -> field.getText().isEmpty());
    editor.setBorder(JBUI.Borders.empty(0, 25));
    editor.setOpaque(true);
    editor.setBackground(MAIN_BG_COLOR);
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
    JPanel panel = new JPanel(new BorderLayout());
    panel.setMinimumSize(new JBDimension(580, 380));

    DefaultActionGroup actions = new DefaultActionGroup();
    actions.add(new DumbAwareAction("Manage Plugin Repositories...") {
      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        if (ShowSettingsUtil.getInstance().editConfigurable(panel, new PluginHostsConfigurable())) {
          // TODO: Auto-generated method stub
        }
      }
    });
    actions.add(new DumbAwareAction(IdeBundle.message("button.http.proxy.settings")) {
      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        if (HttpConfigurable.editConfigurable(panel)) {
          // TODO: Auto-generated method stub
        }
      }
    });
    actions.addSeparator();
    actions.add(new DumbAwareAction("Install Plugin from Disk...") {
      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        InstalledPluginsManagerMain.chooseAndInstall(myPluginsModel, pair -> myPluginsModel.appendOrUpdateDescriptor(pair.second), panel);
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

      JButton backButton = new JButton("Plugins");
      configureBackButton(backButton);

      int currentTab = detailBackTabIndex == -1 ? myTabHeaderComponent.getSelectionTab() : detailBackTabIndex;

      backButton.addActionListener(event -> {
        removeDetailsPanel();
        myCardPanel.select(myCurrentSearchPanel.isEmpty() ? currentTab : myCurrentSearchPanel.tabIndex, true);
        storeSelectionTab(currentTab);
        myTabHeaderComponent.setSelection(currentTab);
      });

      myCardPanel.select(Pair.create(descriptor, label != null && currentTab == UPDATES_TAB), true);
      myPluginsModel.detailPanel.backTabIndex = currentTab;

      myTopController.setLeftComponent(backButton);
      myTabHeaderComponent.clearSelection();
    };

    mySearchListener = (_0, query) -> {
      mySearchTextField.setTextIgnoreEvents(query);
      showSearchPanel(query);
    };

    mySearchTextField.getTextEditor().addFocusListener(new FocusListener() {
      @Override
      public void focusGained(FocusEvent e) {
        if (myCurrentSearchPanel.controller == null) {
          return;
        }
        if (StringUtil.isEmptyOrSpaces(mySearchTextField.getText())) {
          myCurrentSearchPanel.controller.showAttributesPopup(null);
        }
        else {
          myCurrentSearchPanel.controller.handleShowPopup();
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
        }

        //noinspection ConstantConditions,unchecked
        return createDetailsPanel((Pair<IdeaPluginDescriptor, Boolean>)key);
      }
    };
    panel.add(myCardPanel);

    myTabHeaderComponent = new TabHeaderComponent(actions, index -> {
      removeDetailsPanel();
      myCardPanel.select(index, true);
      storeSelectionTab(index);
      updateSearchForSelectedTab(index);
      if (!myCurrentSearchPanel.isEmpty()) {
        myCardPanel.select(myCurrentSearchPanel.tabIndex, true);
      }
    });

    myTabHeaderComponent.addTab("Trending");
    myTabHeaderComponent.addTab("Installed");
    myTabHeaderComponent.addTab(myUpdatesTabName = new CountTabName(myTabHeaderComponent, "Updates"));

    createSearchPanels();

    int selectionTab = getStoredSelectionTab();
    myTabHeaderComponent.setSelection(selectionTab);
    myCardPanel.select(selectionTab, true);
    updateSearchForSelectedTab(selectionTab);

    return panel;
  }

  private void updateSearchForSelectedTab(int index) {
    String text;
    SearchResultPanel searchPanel;
    if (index == TRENDING_TAB) {
      text = "Search trending plugins";
      if (!UpdateSettings.getInstance().getPluginHosts().isEmpty()) {
        text += " and custom repositories";
      }
      searchPanel = myTrendingSearchPanel;
    }
    else if (index == INSTALLED_TAB) {
      text = "Search installed plugins";
      searchPanel = myInstalledSearchPanel;
    }
    else {
      text = "Search available updates";
      searchPanel = myUpdatesSearchPanel;
    }

    StatusText emptyText = mySearchTextField.getTextEditor().getEmptyText();
    emptyText.clear();
    emptyText.appendText(text, new SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, CellPluginComponent.GRAY_COLOR));

    myCurrentSearchPanel = searchPanel;
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
    return PropertiesComponent.getInstance().getInt(SELECTION_TAB_KEY, TRENDING_TAB);
  }

  private static void storeSelectionTab(int value) {
    PropertiesComponent.getInstance().setValue(SELECTION_TAB_KEY, value, TRENDING_TAB);
  }

  @Override
  public void disposeUIResources() {
    myPluginsModel.toBackground();
    Disposer.dispose(mySearchUpdateAlarm);

    if (myShutdownCallback != null) {
      myShutdownCallback.run();
      myShutdownCallback = null;
    }
  }

  @Override
  public void apply() throws ConfigurationException {
    Map<PluginId, Set<PluginId>> dependencies = new HashMap<>(myPluginsModel.getDependentToRequiredListMap());

    for (Iterator<Entry<PluginId, Set<PluginId>>> I = dependencies.entrySet().iterator(); I.hasNext(); ) {
      Entry<PluginId, Set<PluginId>> entry = I.next();
      boolean hasNonModuleDeps = false;

      for (PluginId pluginId : entry.getValue()) {
        if (!PluginManagerCore.isModuleDependency(pluginId)) {
          hasNonModuleDeps = true;
          break;
        }
      }
      if (!hasNonModuleDeps) {
        I.remove();
      }
    }

    if (!dependencies.isEmpty()) {
      throw new ConfigurationException("<html><body style=\"padding: 5px;\">Unable to apply changes: plugin" +
                                       (dependencies.size() == 1 ? " " : "s ") +
                                       StringUtil.join(dependencies.keySet(), pluginId -> {
                                         IdeaPluginDescriptor descriptor = PluginManager.getPlugin(pluginId);
                                         return "\"" + (descriptor == null ? pluginId.getIdString() : descriptor.getName()) + "\"";
                                       }, ", ") +
                                       " won't be able to load.</body></html>");
    }

    int rowCount = myPluginsModel.getRowCount();
    for (int i = 0; i < rowCount; i++) {
      IdeaPluginDescriptor descriptor = myPluginsModel.getObjectAt(i);
      descriptor.setEnabled(myPluginsModel.isEnabled(descriptor.getPluginId()));
    }

    List<String> disableIds = new ArrayList<>();
    for (Entry<PluginId, Boolean> entry : myPluginsModel.getEnabledMap().entrySet()) {
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
        () -> PluginManagerConfigurable.shutdownOrRestartApp(IdeBundle.message("update.notifications.title")), ModalityState.any());
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
      PluginId pluginId = descriptor.getPluginId();
      boolean enabledInTable = myPluginsModel.isEnabled(pluginId);

      if (descriptor.isEnabled() != enabledInTable) {
        if (enabledInTable && !disabledPlugins.contains(pluginId.getIdString())) {
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
    PluginsGroupComponentWithProgress panel =
      new PluginsGroupComponentWithProgress(new PluginsGridLayout(), EventHandler.EMPTY, myNameListener, mySearchListener,
                                            descriptor -> new GridCellPluginComponent(myPluginsModel, descriptor, myTagBuilder));
    panel.getEmptyText().setText("Trending plugins are not loaded.")
      .appendSecondaryText("Check the interner connection.", StatusText.DEFAULT_ATTRIBUTES, null);

    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      List<PluginsGroup> groups = new ArrayList<>();

      try {
        Map<String, IdeaPluginDescriptor> jbRepositoryMap = loadJBRepository();
        Set<String> excludeDescriptors = new HashSet<>();
        addGroup(groups, excludeDescriptors, jbRepositoryMap, "Featured", "is_featured_search=true", "sort_by:featured");
        addGroup(groups, excludeDescriptors, jbRepositoryMap, "New and Updated", "orderBy=update+date", "sort_by:updates");
        addGroup(groups, excludeDescriptors, jbRepositoryMap, "Top Downloads", "orderBy=downloads", "sort_by:downloads");
        addGroup(groups, excludeDescriptors, jbRepositoryMap, "Top Rated", "orderBy=rating", "sort_by:rating");
      }
      catch (IOException e) {
        PluginManagerMain.LOG.info(e);
      }
      finally {
        ApplicationManager.getApplication().invokeLater(() -> {
          panel.stopLoading();

          for (PluginsGroup group : groups) {
            panel.addGroup(group);
          }

          panel.doLayout();
          panel.initialSelection();
        }, ModalityState.any());
      }
    });

    return createScrollPane(panel, false);
  }

  @NotNull
  private Map<String, IdeaPluginDescriptor> loadJBRepository() throws IOException {
    synchronized (myJBRepositoryLock) {
      if (myJBRepositoryMap != null) {
        return myJBRepositoryMap;
      }
    }

    List<IdeaPluginDescriptor> list = new ArrayList<>();
    Map<String, IdeaPluginDescriptor> map = new HashMap<>();
    IOException exception = null;

    for (String host : RepositoryHelper.getPluginHosts()) {
      try {
        for (IdeaPluginDescriptor plugin : RepositoryHelper.loadPlugins(host, null)) {
          String id = plugin.getPluginId().getIdString();
          if (!map.containsKey(id)) {
            list.add(plugin);
            map.put(id, plugin);
          }
        }
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

    synchronized (myJBRepositoryLock) {
      if (myJBRepositoryList == null) {
        myJBRepositoryList = list;
        myJBRepositoryMap = map;
      }
      return myJBRepositoryMap;
    }
  }

  private void addGroup(@NotNull List<PluginsGroup> groups,
                        @NotNull Set<String> excludeDescriptors,
                        @NotNull Map<String, IdeaPluginDescriptor> jbRepositoryMap,
                        @NotNull String name,
                        @NotNull String query,
                        @NotNull String showAllQuery) throws IOException {
    PluginsGroup group = new PluginsGroup(name);
    loadPlugins(group.descriptors, jbRepositoryMap, excludeDescriptors, query, 9);

    if (!group.descriptors.isEmpty()) {
      //noinspection unchecked
      group.rightAction = new LinkLabel("Show All", null, mySearchListener, showAllQuery);
      groups.add(group);
    }
  }

  @NotNull
  private JComponent createInstalledPanel() {
    PluginsGroupComponent panel =
      new PluginsGroupComponent(new PluginsListLayout(), new MultiSelectionEventHandler(), myNameListener, mySearchListener,
                                descriptor -> new ListPluginComponent(myPluginsModel, descriptor, false));
    registerCopyProvider(panel);

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
    return createScrollPane(panel, true);
  }

  @NotNull
  private JComponent createUpdatesPanel() {
    PluginsGroupComponentWithProgress panel =
      new PluginsGroupComponentWithProgress(new PluginsListLayout(), new MultiSelectionEventHandler(), myNameListener, mySearchListener,
                                            descriptor -> new ListPluginComponent(myPluginsModel, descriptor, true));
    panel.getEmptyText().setText("No updates available.");
    registerCopyProvider(panel);

    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      Collection<PluginDownloader> updates = UpdateChecker.getPluginUpdates();

      ApplicationManager.getApplication().invokeLater(() -> {
        panel.stopLoading();

        if (ContainerUtil.isEmpty(updates)) {
          myUpdatesTabName.setCount(0);
        }
        else {
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
              myUpdatesTabName.setCount(count);
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

          group.sortByName();
          panel.addGroup(group);
          group.titleWithCount();

          myPluginsModel.setUpdateGroup(group);
        }

        panel.doLayout();
        panel.initialSelection();
      }, ModalityState.any());
    });

    myUpdatesPanel = panel;
    return createScrollPane(panel, false);
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
        attributes.add("sort_by:"); // XXX: ignore if sets `repository`
        return attributes;
      }

      @Nullable
      @Override
      protected List<String> getValues(@NotNull String attribute) {
        switch (attribute) {
          case "tag:":
            if (ContainerUtil.isEmpty(myAllTagSorted)) {
              Set<String> allTags = new HashSet<>();
              for (IdeaPluginDescriptor descriptor : getJBRepositoryList()) {
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
            return ContainerUtil.concat(ContainerUtil.list("JetBrains"), UpdateSettings.getInstance().getPluginHosts());
          case "sort_by:":
            return ContainerUtil.list("downloads", "name", "rating", "featured", "updates"); // XXX: "name" if sets `repository`
        }
        return null;
      }

      @Override
      protected void showPopupForQuery() {
        String query = mySearchTextField.getText();
        if (mySearchTextField.getTextEditor().getCaretPosition() < query.length()) {
          hidePopup();
          return;
        }

        List<IdeaPluginDescriptor> result = localSearchPlugins(query);
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
          hidePopup();
          createPopup(new CollectionListModel<>(result), SearchPopup.Type.SearchQuery);
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

            String splitter = (String)myPopup.data;
            for (String partName : SearchQueryParser.split(descriptor.getName(), splitter)) {
              append(partName, partName.equalsIgnoreCase(splitter)
                               ? SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES
                               : SimpleTextAttributes.REGULAR_ATTRIBUTES);
            }

            if (isJBPlugin(descriptor)) {
              append(" by JetBrains", SimpleTextAttributes.GRAY_ATTRIBUTES);
            }
            else {
              String vendor = descriptor.getVendor();
              if (!StringUtil.isEmptyOrSpaces(vendor)) {
                append(" by " + StringUtil.shortenPathWithEllipsis(vendor, 50), SimpleTextAttributes.GRAY_ATTRIBUTES);
              }
            }
          }
        };

        myPopup.createAndShow(callback, renderer, async);
      }

      @NotNull
      private List<IdeaPluginDescriptor> localSearchPlugins(@NotNull String query) {
        IdeaPluginDescriptor descriptorEquals = null;
        List<IdeaPluginDescriptor> descriptorsStartWith = new ArrayList<>();
        List<IdeaPluginDescriptor> descriptorsContains = new ArrayList<>();

        for (IdeaPluginDescriptor descriptor : getJBRepositoryList()) {
          String name = descriptor.getName();
          if (descriptorEquals == null && name.equalsIgnoreCase(query)) {
            descriptorEquals = descriptor;
          }
          else if (StringUtil.startsWithIgnoreCase(name, query)) {
            descriptorsStartWith.add(descriptor);
          }
          else if (StringUtil.containsIgnoreCase(name, query)) {
            descriptorsContains.add(descriptor);
          }
        }

        List<IdeaPluginDescriptor> result = new ArrayList<>();
        if (descriptorEquals != null) {
          result.add(descriptorEquals);
        }

        PluginsGroup.sortByName(descriptorsStartWith);
        result.addAll(descriptorsStartWith);

        PluginsGroup.sortByName(descriptorsContains);
        result.addAll(descriptorsContains);

        return result;
      }
    };
    myTrendingSearchPanel =
      new SearchResultPanel(trendingController, createSearchPanelComponentWithProgress(), TRENDING_SEARCH_TAB, TRENDING_TAB) {
        @Override
        protected void handleQuery(@NotNull String query, @NotNull PluginsGroup result) {
          try {
            Map<String, IdeaPluginDescriptor> jbRepositoryMap = loadJBRepository();
            SearchQueryParser.Trending parser = new SearchQueryParser.Trending(query);

            for (String pluginId : requestToPluginRepository(createSearchUrl(parser.getUrlQuery(), 10000), forceHttps())) {
              IdeaPluginDescriptor descriptor = jbRepositoryMap.get(pluginId);
              if (descriptor != null) {
                result.descriptors.add(descriptor);
              }
            }
          }
          catch (IOException e) {
            PluginManagerMain.LOG.info(e);
          }
        }
      };

    SearchPopupController installedController = new SearchPopupController(mySearchTextField) {
      @NotNull
      @Override
      protected List<String> getAttributes() {
        return ContainerUtil.list("status:");
      }

      @Nullable
      @Override
      protected List<String> getValues(@NotNull String attribute) {
        return attribute.equals("status:") ? ContainerUtil
          .list("disabled", "enabled", "inactive", "installed", "bundled", "invalid", "outdated", "uninstalled") : null;
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

  private static void loadPlugins(@NotNull List<IdeaPluginDescriptor> descriptors,
                                  @NotNull Map<String, IdeaPluginDescriptor> allDescriptors,
                                  @NotNull Set<String> excludeDescriptors,
                                  @NotNull String query,
                                  int count) throws IOException {
    boolean forceHttps = forceHttps();
    Url baseUrl = createSearchUrl(query, count);
    Url offsetUrl = baseUrl;
    Map<String, String> offsetParameters = new HashMap<>();
    int offset = 0;

    while (true) {
      List<String> pluginIds = requestToPluginRepository(offsetUrl, forceHttps);
      if (pluginIds.isEmpty()) {
        return;
      }

      for (String pluginId : pluginIds) {
        IdeaPluginDescriptor descriptor = allDescriptors.get(pluginId);
        if (descriptor != null && excludeDescriptors.add(pluginId) && PluginManager.getPlugin(descriptor.getPluginId()) == null) {
          descriptors.add(descriptor);
          if (descriptors.size() == count) {
            return;
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

    HttpRequests.request(url).forceHttps(forceHttps).productNameAsUserAgent().connect(request -> {
      URLConnection connection = request.getConnection();
      if (connection instanceof HttpURLConnection && ((HttpURLConnection)connection).getResponseCode() != HttpURLConnection.HTTP_OK) {
        return null;
      }

      try (JsonReaderEx json = new JsonReaderEx(FileUtil.loadTextAndClose(request.getReader()))) {
        json.beginArray();
        while (json.hasNext()) {
          ids.add(json.nextString());
        }
        json.endArray();
      }

      return null;
    });

    return ids;
  }

  @NotNull
  private static Url createSearchUrl(@NotNull String query, int count) {
    return Urls.newFromEncoded("http://plugins.jetbrains.com/api/search?" + query +
                               "&build=" + ApplicationInfoImpl.getShadowInstance().getApiVersion() +
                               "&max=" + count);
  }

  private static boolean forceHttps() {
    return IdeaApplication.isLoaded() && UpdateSettings.getInstance().canUseSecureConnection();
  }

  @NotNull
  private List<IdeaPluginDescriptor> getJBRepositoryList() {
    synchronized (myJBRepositoryLock) {
      if (myJBRepositoryList != null) {
        return myJBRepositoryList;
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
    return Collections.emptyList(); // XXX
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
  public static String getErrorMessage(@NotNull InstalledPluginsTableModel pluginsModel,
                                       @NotNull PluginDescriptor pluginDescriptor,
                                       @NotNull Ref<Boolean> enableAction) {
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
              List<String> modules = ((IdeaPluginDescriptorImpl)descriptor).getModules();
              if (modules != null && modules.contains(id.getIdString())) {
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
  public static String getDownloads(@NotNull IdeaPluginDescriptor plugin) {
    String downloads = ((PluginNode)plugin).getDownloads();
    if (!StringUtil.isEmptyOrSpaces(downloads)) {
      try {
        Long value = Long.valueOf(downloads);
        if (value > 1000) {
          return value < 1000000 ? K_FORMAT.format(value / 1000D) : M_FORMAT.format(value / 1000000D);
        }
      }
      catch (NumberFormatException ignore) {
      }
    }

    return null;
  }

  @Nullable
  public static String getLastUpdatedDate(@NotNull IdeaPluginDescriptor plugin) {
    long date = ((PluginNode)plugin).getDate();
    return date > 0 ? DATE_FORMAT.format(new Date(date)) : null;
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

    public CountTabName(@NotNull TabHeaderComponent component, @NotNull String baseName) {
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

  public static final Color DisabledColor = new JBColor(0xB1B1B1, 0x696969);

  private static void configureBackButton(@NotNull JButton button) {
    button.setIcon(new Icon() {
      @Override
      public void paintIcon(Component c, Graphics g, int x, int y) {
        AllIcons.Actions.Back.paintIcon(c, g, x + JBUI.scale(7), y);
      }

      @Override
      public int getIconWidth() {
        return AllIcons.Actions.Back.getIconWidth() + JBUI.scale(7);
      }

      @Override
      public int getIconHeight() {
        return AllIcons.Actions.Back.getIconHeight();
      }
    });

    button.setHorizontalAlignment(SwingConstants.LEFT);

    Dimension size = button.getPreferredSize();
    size.width -= JBUI.scale(15);
    button.setPreferredSize(size);
  }

  public static void setWidth72(@NotNull JButton button) {
    int width = JBUI.scale(72);
    if (button instanceof JBOptionButton && button.getComponentCount() == 2) {
      width += button.getComponent(1).getPreferredSize().width;
    }
    else {
      Border border = button.getBorder();
      if (border != null) {
        Insets insets = border.getBorderInsets(button);
        width += insets.left + insets.right;
      }
    }
    button.setPreferredSize(new Dimension(width, button.getPreferredSize().height));
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
    return new PluginsGroupComponentWithProgress(new PluginsGridLayout(), EventHandler.EMPTY, myNameListener, mySearchListener,
                                                 descriptor -> new GridCellPluginComponent(myPluginsModel, descriptor, myTagBuilder));
  }
}