// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins;

import com.intellij.icons.AllIcons;
import com.intellij.ide.HelpTooltip;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.impl.ProjectUtil;
import com.intellij.ide.ui.LafManager;
import com.intellij.ide.ui.search.SearchableOptionsRegistrar;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.idea.IdeaApplication;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.application.impl.ApplicationImpl;
import com.intellij.openapi.application.impl.ApplicationInfoImpl;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.fileChooser.ex.FileTextFieldImpl;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.options.newEditor.SettingsDialog;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.progress.TaskInfo;
import com.intellij.openapi.progress.util.AbstractProgressIndicatorExBase;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.popup.*;
import com.intellij.openapi.updateSettings.impl.PluginDownloader;
import com.intellij.openapi.updateSettings.impl.UpdateChecker;
import com.intellij.openapi.updateSettings.impl.UpdateSettings;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.ex.ProgressIndicatorEx;
import com.intellij.openapi.wm.ex.StatusBarEx;
import com.intellij.openapi.wm.impl.status.InlineProgressIndicator;
import com.intellij.openapi.wm.impl.welcomeScreen.WelcomeFrame;
import com.intellij.ui.*;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.components.*;
import com.intellij.ui.components.breadcrumbs.Breadcrumbs;
import com.intellij.ui.components.labels.LinkLabel;
import com.intellij.ui.components.labels.LinkListener;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.ui.components.panels.OpaquePanel;
import com.intellij.ui.components.panels.Wrapper;
import com.intellij.util.*;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.io.HttpRequests;
import com.intellij.util.io.URLUtil;
import com.intellij.util.net.HttpConfigurable;
import com.intellij.util.ui.*;
import com.intellij.xml.util.XmlStringUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.io.JsonReaderEx;
import sun.swing.SwingUtilities2;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.event.DocumentEvent;
import javax.swing.text.BadLocationException;
import javax.swing.text.View;
import java.awt.*;
import java.awt.event.*;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URLConnection;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

import static com.intellij.util.ui.UIUtil.uiChildren;

/**
 * @author Alexander Lobas
 */
public class PluginManagerConfigurableNew
  implements SearchableConfigurable, Configurable.NoScroll, Configurable.NoMargin, Configurable.TopComponentProvider {
  public static final String ID = "preferences.pluginManager";

  private static final String SELECTION_TAB_KEY = "PluginConfigurable.selectionTab";

  private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("MMM dd, yyyy");
  private static final DecimalFormat K_FORMAT = new DecimalFormat("###.#K");
  private static final DecimalFormat M_FORMAT = new DecimalFormat("###.#M");

  @SuppressWarnings("UseJBColor")
  private static final Color MAIN_BG_COLOR = new JBColor(() -> JBColor.isBright() ? UIUtil.getListBackground() : new Color(0x313335));

  private final TagBuilder myTagBuilder;

  private LinkListener<IdeaPluginDescriptor> myNameListener;
  private LinkListener<String> mySearchListener;

  private CardLayoutPanel<Object, Object, JComponent> myCardPanel;
  private TabHeaderComponent myTabHeaderComponent;
  private CountTabName myUpdatesTabName;
  private TopComponentController myTopController;

  private final SearchTextField mySearchTextField;
  private SearchResultInfo mySearchResultInfo;
  private boolean mySkipDocumentEvents;
  private final Alarm mySearchUpdateAlarm = new Alarm();
  private JBPopup mySearchPopup;
  private CollectionListModel<IdeaPluginDescriptor> mySearchPopupModel;
  private JList<IdeaPluginDescriptor> mySearchPopupList;
  private Pair<PluginsGroup, PluginsGroup> mySearchFlyResult;

  private final MyPluginModel myPluginsModel = new MyPluginModel();

  private Runnable myShutdownCallback;

  private List<IdeaPluginDescriptor> myJBRepositoryList;
  private Map<String, IdeaPluginDescriptor> myJBRepositoryMap;
  private final Object myJBRepositoryLock = new Object();

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

    mySearchTextField = new SearchTextField() {
      @Override
      public void setBounds(int x, int y, int width, int height) {
        super.setBounds(x - 1, y, width + 2, height);
      }

      @Override
      public Dimension getPreferredSize() {
        Dimension size = super.getPreferredSize();
        size.height = JBUI.scale(38);
        return size;
      }

      @Override
      protected boolean preprocessEventForTextField(KeyEvent event) {
        int keyCode = event.getKeyCode();
        int id = event.getID();

        if (keyCode == KeyEvent.VK_ENTER || event.getKeyChar() == '\n') {
          if (id == KeyEvent.KEY_PRESSED) {
            if (mySearchPopupList != null && mySearchPopupList.getSelectedIndex() != -1) {
              mySearchPopupList.dispatchEvent(event);
            }
            else {
              showSearchResultPanel();
            }
          }
          return true;
        }
        if ((keyCode == KeyEvent.VK_DOWN || keyCode == KeyEvent.VK_UP) && id == KeyEvent.KEY_PRESSED && mySearchPopupList != null) {
          if (keyCode == KeyEvent.VK_DOWN && mySearchPopupList.getSelectedIndex() == -1) {
            mySearchPopupList.setSelectedIndex(0);
          }
          else {
            mySearchPopupList.dispatchEvent(event);
          }
          return true;
        }
        return super.preprocessEventForTextField(event);
      }

      @Override
      protected void onFieldCleared() {
        hideSearchResultPanel();
      }
    };
    mySearchTextField.setBorder(JBUI.Borders.customLine(new JBColor(0xC5C5C5, 0x515151)));

    JBTextField editor = mySearchTextField.getTextEditor();
    editor.putClientProperty("JTextField.Search.Gap", JBUI.scale(8 - 25));
    editor.putClientProperty("JTextField.Search.GapEmptyText", JBUI.scale(8));
    editor.putClientProperty("StatusVisibleFunction", (BooleanFunction<JBTextField>)field -> field.getText().isEmpty());
    editor.setBorder(JBUI.Borders.empty(0, 25));
    editor.getEmptyText().appendText("Search plugins");
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
    panel.setMinimumSize(new Dimension(JBUI.scale(580), -1));

    DefaultActionGroup actions = new DefaultActionGroup();
    actions.add(new DumbAwareAction("Manage Plugin Repositories...") {
      @Override
      public void actionPerformed(AnActionEvent e) {
        if (ShowSettingsUtil.getInstance().editConfigurable(panel, new PluginHostsConfigurable())) {
          // TODO: Auto-generated method stub
        }
      }
    });
    actions.add(new DumbAwareAction(IdeBundle.message("button.http.proxy.settings")) {
      @Override
      public void actionPerformed(AnActionEvent e) {
        if (HttpConfigurable.editConfigurable(panel)) {
          // TODO: Auto-generated method stub
        }
      }
    });
    actions.addSeparator();
    actions.add(new DumbAwareAction("Install Plugin from Disk...") {
      @Override
      public void actionPerformed(AnActionEvent e) {
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
        hideSearchResultPanel();
        removeDetailsPanel();
      }

      assert myPluginsModel.detailPanel == null;

      JButton backButton = new JButton(mySearchResultInfo == null ? "Plugins" : "Search");
      configureBackButton(backButton);

      if (mySearchResultInfo == null) {
        int currentTab = detailBackTabIndex == -1 ? myTabHeaderComponent.getSelectionTab() : detailBackTabIndex;

        backButton.addActionListener(event -> {
          removeDetailsPanel();
          myCardPanel.select(currentTab, true);
          storeSelectionTab(currentTab);
          myTabHeaderComponent.setSelection(currentTab);
        });

        myCardPanel.select(Pair.create(descriptor, label != null && currentTab == 2), true);
        myPluginsModel.detailPanel.backTabIndex = currentTab;
      }
      else {
        backButton.addActionListener(event -> {
          removeDetailsPanel();
          mySearchResultInfo.setText();
          myCardPanel.select(mySearchResultInfo.key, true);
        });
        mySearchResultInfo.clearText();
        myCardPanel.select(Pair.create(descriptor, Boolean.FALSE), true);
      }

      myTopController.setLeftComponent(backButton);
      myTabHeaderComponent.clearSelection();
    };

    mySearchListener = (_0, query) -> showSearchResultPanel(query, true);

    mySearchTextField.getTextEditor().getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(DocumentEvent e) {
        if (mySkipDocumentEvents) {
          return;
        }

        mySearchUpdateAlarm.cancelAllRequests();
        mySearchUpdateAlarm.addRequest(this::searchOnTheFly, 100, ModalityState.stateForComponent(mySearchTextField));
      }

      private void searchOnTheFly() {
        String query = mySearchTextField.getText();
        boolean empty = StringUtil.isEmptyOrSpaces(query);
        if (empty || query.length() < 3) {
          hideSearchPopup();
          if (empty) {
            hideSearchResultPanel();
          }
          return;
        }

        mySearchFlyResult = localSearchPlugins(query);
        if (mySearchFlyResult.first == null && mySearchFlyResult.second == null) {
          hideSearchPopup();
          return;
        }

        if (mySearchPopupModel == null) {
          mySearchPopupModel = new CollectionListModel<>();
        }
        else {
          mySearchPopupModel.removeAll();
        }

        if (mySearchFlyResult.first != null) {
          mySearchPopupModel.addAll(mySearchPopupModel.getSize(), mySearchFlyResult.first.descriptors);
        }
        if (mySearchFlyResult.second != null) {
          mySearchPopupModel.addAll(mySearchPopupModel.getSize(), mySearchFlyResult.second.descriptors);
        }

        JBTextField editor = mySearchTextField.getTextEditor();
        Point location = FileTextFieldImpl.getLocationForCaret(editor);

        if (mySearchPopup != null) {
          mySearchPopup.setLocation(location);
          mySearchPopup.pack(false, true);
          return;
        }

        Consumer<IdeaPluginDescriptor> callback = descriptor -> {
          hideSearchPopup();
          setSearchTextIgnoreEvents("");
          myNameListener.linkSelected(null, descriptor);
        };

        ColoredListCellRenderer renderer = new ColoredListCellRenderer() {
          @Override
          protected void customizeCellRenderer(@NotNull JList list,
                                               Object value, int index, boolean selected, boolean hasFocus) {
            IdeaPluginDescriptor descriptor = (IdeaPluginDescriptor)value;
            append(descriptor.getName(), mySearchFlyResult.first != null && mySearchFlyResult.first.descriptors.contains(descriptor)
                                         ? SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES
                                         : SimpleTextAttributes.REGULAR_ATTRIBUTES);
            if (isJBPlugin(descriptor)) {
              append(" by JetBrains", SimpleTextAttributes.GRAY_ATTRIBUTES);
            }
            else {
              String vendor = descriptor.getVendor();
              if (!StringUtil.isEmptyOrSpaces(vendor)) {
                append(" by " + vendor, SimpleTextAttributes.GRAY_ATTRIBUTES);
              }
            }
          }
        };

        mySearchPopup = JBPopupFactory.getInstance().createListPopupBuilder(mySearchPopupList = new JBList<>(mySearchPopupModel))
                                      .setMovable(false).setResizable(false).setRequestFocus(false)
                                      .setItemChosenCallback(callback)
                                      .setRenderer(renderer).createPopup();

        mySearchPopup.addListener(new JBPopupAdapter() {
          @Override
          public void onClosed(LightweightWindowEvent event) {
            mySearchPopup = null;
            mySearchPopupModel = null;
            mySearchPopupList = null;
            PluginManagerConfigurableNew.this.mySearchFlyResult = null;
          }
        });

        mySearchPopupList.clearSelection();
        mySearchPopup.showInScreenCoordinates(editor, location);
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
          if (index == 0) {
            return createTrendingPanel();
          }
          if (index == 1) {
            return createInstalledPanel();
          }
          if (index == 2) {
            return createUpdatesPanel();
          }
        }

        if (key instanceof String) {
          assert mySearchResultInfo == null;
          mySearchResultInfo = new SearchResultInfo((String)key);
          return mySearchResultInfo.createScrollPane();
        }

        //noinspection ConstantConditions,unchecked
        Pair<IdeaPluginDescriptor, Boolean> data = (Pair<IdeaPluginDescriptor, Boolean>)key;
        return myPluginsModel.detailPanel = new DetailsPagePluginComponent(data.first, data.second);
      }
    };
    panel.add(myCardPanel);

    myTabHeaderComponent = new TabHeaderComponent(actions, index -> {
      removeDetailsPanel();
      removeSearchResultPanel();
      myCardPanel.select(index, true);
      storeSelectionTab(index);
    });

    myTabHeaderComponent.addTab("Trending");
    myTabHeaderComponent.addTab("Installed");
    myTabHeaderComponent.addTab(myUpdatesTabName = new CountTabName(myTabHeaderComponent, "Updates"));

    int selectionTab = getStoredSelectionTab();
    myTabHeaderComponent.setSelection(selectionTab);
    myCardPanel.select(selectionTab, true);

    return panel;
  }

  private void removeDetailsPanel() {
    if (myPluginsModel.detailPanel != null) {
      myPluginsModel.detailPanel.close();
      myPluginsModel.detailPanel = null;
      myTopController.setLeftComponent(null);
      myCardPanel.remove(myCardPanel.getComponentCount() - 1);
    }
  }

  private void showSearchResultPanel() {
    hideSearchPopup();

    String query = mySearchTextField.getText();
    if (StringUtil.isEmptyOrSpaces(query)) {
      hideSearchResultPanel();
    }
    else if (mySearchResultInfo == null) {
      showSearchResultPanel(query, false);
    }
    else {
      if (myPluginsModel.detailPanel != null) {
        removeDetailsPanel();
        mySearchResultInfo.setText();
        myCardPanel.select(mySearchResultInfo.key, true);
      }
      mySearchResultInfo.setQuery(query);
    }
  }

  private void hideSearchResultPanel() {
    if (mySearchResultInfo != null) {
      int index = mySearchResultInfo.backTabIndex;
      removeSearchResultPanel();
      myCardPanel.select(index, true);
      myTabHeaderComponent.setSelection(index);
      storeSelectionTab(index);
    }
  }

  private void showSearchResultPanel(@NotNull String query, boolean updateText) {
    int index = mySearchResultInfo == null ? (myPluginsModel.detailPanel == null
                                              ? myTabHeaderComponent.getSelectionTab()
                                              : myPluginsModel.detailPanel.backTabIndex) : mySearchResultInfo.backTabIndex;
    removeDetailsPanel();
    removeSearchResultPanel();
    myCardPanel.select(query, true);
    mySearchResultInfo.backTabIndex = index;
    if (updateText) {
      mySearchResultInfo.setText();
    }
    myTabHeaderComponent.clearSelection();
  }

  private void removeSearchResultPanel() {
    if (mySearchResultInfo != null) {
      mySearchResultInfo.close();
      mySearchResultInfo = null;
      mySearchTextField.setText("");
      myCardPanel.remove(myCardPanel.getComponentCount() - 1);
    }
  }

  private static int getStoredSelectionTab() {
    return PropertiesComponent.getInstance().getInt(SELECTION_TAB_KEY, 0);
  }

  private static void storeSelectionTab(int value) {
    PropertiesComponent.getInstance().setValue(SELECTION_TAB_KEY, value, 0);
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

      for (PluginId pluginId: entry.getValue()) {
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
    for (Entry<PluginId, Boolean> entry: myPluginsModel.getEnabledMap().entrySet()) {
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

    for (Entry<PluginId, Boolean> entry: myPluginsModel.getEnabledMap().entrySet()) {
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
        addGroup(groups, excludeDescriptors, jbRepositoryMap, "Featured", "is_featured_search=true", "sort by:featured");
        addGroup(groups, excludeDescriptors, jbRepositoryMap, "New and Updated", "orderBy=update+date", "sort by:updates");
        addGroup(groups, excludeDescriptors, jbRepositoryMap, "Top Downloads", "orderBy=downloads", "sort by:downloads");
        addGroup(groups, excludeDescriptors, jbRepositoryMap, "Top Rated", "orderBy=rating", "sort by:rating");
      }
      catch (IOException e) {
        PluginManagerMain.LOG.info(e);
      }
      finally {
        ApplicationManager.getApplication().invokeLater(() -> {
          panel.stopLoading();

          for (PluginsGroup group: groups) {
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

    List<IdeaPluginDescriptor> list = RepositoryHelper.loadPlugins(null);
    Map<String, IdeaPluginDescriptor> map = new HashMap<>();

    for (IdeaPluginDescriptor plugin: list) {
      map.put(plugin.getPluginId().getIdString(), plugin);
    }

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
    loadPlugins(group.descriptors, jbRepositoryMap, excludeDescriptors, query, 6);

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

    for (IdeaPluginDescriptor descriptor: PluginManagerCore.getPlugins()) {
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

    return createScrollPane(panel, true);
  }

  @NotNull
  private JComponent createUpdatesPanel() {
    PluginsGroupComponentWithProgress panel =
      new PluginsGroupComponentWithProgress(new PluginsListLayout(), new MultiSelectionEventHandler(), myNameListener, mySearchListener,
                                            descriptor -> new ListPluginComponent(myPluginsModel, descriptor, true));
    panel.getEmptyText().setText("No updates available.");

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
              for (CellPluginComponent component: ui.plugins) {
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
              for (CellPluginComponent component: group.ui.plugins) {
                ((ListPluginComponent)component).updatePlugin();
              }
            }
          }, null);

          for (PluginDownloader toUpdateDownloader: updates) {
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

    return createScrollPane(panel, false);
  }

  @Nullable
  private static String recognizeServerSearchQuery(@NotNull String query) {
    if (query.equals("sort by:featured")) {
      return "is_featured_search=true";
    }
    if (query.equals("sort by:updates")) {
      return "orderBy=update+date";
    }
    if (query.equals("sort by:downloads")) {
      return "orderBy=downloads";
    }
    if (query.equals("sort by:rating")) {
      return "orderBy=rating";
    }

    if (query.startsWith("tag:")) {
      return "tags=" + URLUtil.encodeURIComponent(query.substring(4));
    }

    return null;
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

      for (String pluginId: pluginIds) {
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
  private Pair<PluginsGroup, PluginsGroup> localSearchPlugins(@NotNull String query) {
    Set<String> search = SearchableOptionsRegistrar.getInstance().getProcessedWords(query);

    PluginsGroup installed = new PluginsGroup("Installed");
    localSearchPlugins(MyPluginModel.getInstallingPlugins(), installed.descriptors, descriptor -> true, query, search);
    localSearchPlugins(InstalledPluginsState.getInstance().getInstalledPlugins(), installed.descriptors, descriptor -> true, query, search);

    ApplicationInfoEx appInfo = ApplicationInfoEx.getInstanceEx();
    localSearchPlugins(PluginManagerCore.getPlugins(), installed.descriptors,
                       descriptor -> !appInfo.isEssentialPlugin(descriptor.getPluginId().getIdString()), query, search);

    Set<String> installedIds = new HashSet<>();
    for (IdeaPluginDescriptor descriptor: installed.descriptors) {
      installedIds.add(descriptor.getPluginId().getIdString());
    }

    PluginsGroup repository = new PluginsGroup("Search Result");
    localSearchPlugins(getJBRepositoryList(), repository.descriptors,
                       descriptor -> !installedIds.contains(descriptor.getPluginId().getIdString()),
                       query, search);

    if (installed.descriptors.isEmpty()) {
      installed = null;
    }
    else {
      installed.sortByName();
    }
    if (repository.descriptors.isEmpty()) {
      repository = null;
    }
    else {
      repository.sortByName();
    }
    return Pair.create(installed, repository);
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

  private static void localSearchPlugins(@NotNull Collection<IdeaPluginDescriptor> source,
                                         @NotNull List<IdeaPluginDescriptor> result,
                                         @NotNull BooleanFunction<IdeaPluginDescriptor> accept,
                                         @NotNull String query,
                                         @NotNull Set<String> search) {
    for (IdeaPluginDescriptor descriptor: source) {
      if (accept.fun(descriptor) && PluginManagerMain.isAccepted(query, search, descriptor)) {
        result.add(descriptor);
      }
    }
  }

  private static void localSearchPlugins(@NotNull IdeaPluginDescriptor[] source,
                                         @NotNull List<IdeaPluginDescriptor> result,
                                         @NotNull BooleanFunction<IdeaPluginDescriptor> accept,
                                         @NotNull String query,
                                         @NotNull Set<String> search) {
    for (IdeaPluginDescriptor descriptor: source) {
      if (accept.fun(descriptor) && PluginManagerMain.isAccepted(query, search, descriptor)) {
        result.add(descriptor);
      }
    }
  }

  @NotNull
  private static JPanel createLabelsPanel(@NotNull JPanel parent,
                                          @NotNull List<JLabel> labels,
                                          @NotNull String labelText,
                                          @NotNull String text,
                                          @Nullable String link) {
    JPanel linePanel = new NonOpaquePanel(new HorizontalLayout(5));
    parent.add(linePanel);

    JLabel label = new JLabel(labelText);
    linePanel.add(label);
    labels.add(label);

    if (StringUtil.isEmptyOrSpaces(link)) {
      linePanel.add(new JLabel(text));
    }
    else {
      linePanel.add(new LinkLabel(text, null));
    }

    return linePanel;
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
  private static String getErrorMessage(@NotNull InstalledPluginsTableModel pluginsModel,
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
          for (IdeaPluginDescriptor descriptor: PluginManagerCore.getPlugins()) {
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
  private static List<String> getTags(@NotNull IdeaPluginDescriptor plugin) {
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
  private static String getDownloads(@NotNull IdeaPluginDescriptor plugin) {
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
  private static String getLastUpdatedDate(@NotNull IdeaPluginDescriptor plugin) {
    long date = ((PluginNode)plugin).getDate();
    return date > 0 ? DATE_FORMAT.format(new Date(date)) : null;
  }

  @Nullable
  private static String getRating(@NotNull IdeaPluginDescriptor plugin) {
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

  private interface TabHeaderListener {
    void selectionChanged(int index);
  }

  @NotNull
  private static JComponent createToolbar(@NotNull DefaultActionGroup actions) {
    DefaultActionGroup toolbarActionGroup = new DefaultActionGroup();
    ActionToolbar toolbar =
      ActionManager.getInstance().createActionToolbar(ActionPlaces.NAVIGATION_BAR_TOOLBAR, toolbarActionGroup, true);
    toolbar.setReservePlaceAutoPopupIcon(false);
    toolbar.setLayoutPolicy(ActionToolbar.NOWRAP_LAYOUT_POLICY);
    JComponent toolbarComponent = toolbar.getComponent();
    toolbarActionGroup.add(new DumbAwareAction(null, null, AllIcons.General.Gear) {
      @Override
      public void actionPerformed(AnActionEvent e) {
        ListPopup actionGroupPopup = JBPopupFactory.getInstance().
          createActionGroupPopup(null, actions, e.getDataContext(), true, null, Integer.MAX_VALUE);

        HelpTooltip.setMasterPopup(e.getInputEvent().getComponent(), actionGroupPopup);
        actionGroupPopup.show(new RelativePoint(toolbarComponent, getPopupPoint()));
      }

      private Point getPopupPoint() {
        int dH = UIUtil.isUnderWin10LookAndFeel() ? JBUI.scale(1) : 0;
        return new Point(JBUI.scale(2), toolbarComponent.getHeight() - dH);
      }
    });
    toolbarComponent.setBorder(JBUI.Borders.empty());
    return toolbarComponent;
  }

  private static class TabHeaderComponent extends JComponent {
    private final List<Computable<String>> myTabs = new ArrayList<>();
    private final JComponent myToolbarComponent;
    private final TabHeaderListener myListener;
    private int mySelectionTab = -1;
    private int myHoverTab = -1;
    private SizeInfo mySizeInfo;
    private int myBaselineY;
    private Breadcrumbs myBreadcrumbs;

    public TabHeaderComponent(@NotNull DefaultActionGroup actions, @NotNull TabHeaderListener listener) {
      myListener = listener;
      add(myToolbarComponent = createToolbar(actions));
      setBackground(JBUI.CurrentTheme.ToolWindow.headerBackground());
      setOpaque(true);

      MouseAdapter mouseHandler = new MouseAdapter() {
        @Override
        public void mouseClicked(MouseEvent event) {
          if (SwingUtilities.isLeftMouseButton(event)) {
            int tab = findTab(event);
            if (tab != -1 && tab != mySelectionTab) {
              mySelectionTab = tab;
              myListener.selectionChanged(tab);
              repaint();
            }
          }
        }

        @Override
        public void mouseExited(MouseEvent e) {
          if (myHoverTab != -1) {
            myHoverTab = -1;
            repaint();
          }
        }

        @Override
        public void mouseMoved(MouseEvent event) {
          int tab = findTab(event);
          if (tab != -1 && tab != myHoverTab) {
            myHoverTab = tab;
            repaint();
          }
        }
      };
      addMouseListener(mouseHandler);
      addMouseMotionListener(mouseHandler);
    }

    public void addTab(@NotNull String title) {
      addTab(() -> title);
    }

    public void addTab(@NotNull Computable<String> titleComputable) {
      myTabs.add(titleComputable);
      update();
    }

    public void update() {
      mySizeInfo = null;
      revalidate();
      repaint();
    }

    public int getSelectionTab() {
      return mySelectionTab;
    }

    public void clearSelection() {
      setSelection(-1);
    }

    public void setSelection(int index) {
      if (index < 0) {
        mySelectionTab = -1;
      }
      else if (index >= myTabs.size()) {
        mySelectionTab = myTabs.size() - 1;
      }
      else {
        mySelectionTab = index;
      }
      repaint();
    }

    private int findTab(@NotNull MouseEvent event) {
      calculateSize();
      int x = getStartX();
      int height = getHeight();
      int eventX = event.getX();
      int eventY = event.getY();

      for (int i = 0, size = myTabs.size(); i < size; i++) {
        Rectangle bounds = mySizeInfo.tabs[i];
        if (new Rectangle(x + bounds.x, 0, bounds.width, height).contains(eventX, eventY)) {
          return i;
        }
      }

      return -1;
    }

    @Override
    protected void paintComponent(Graphics g) {
      super.paintComponent(g);

      if (g instanceof Graphics2D) {
        ((Graphics2D)g).setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
      }

      calculateSize();

      FontMetrics fm = getFontMetrics(getFont());
      int x = getStartX();
      int height = getHeight();
      int tabTitleY = fm.getAscent() + (height - fm.getHeight()) / 2;
      if (myBreadcrumbs != null) {
        tabTitleY = myBaselineY + myBreadcrumbs.getBaseline();
      }

      for (int i = 0, size = myTabs.size(); i < size; i++) {
        if (mySelectionTab == i || myHoverTab == i) {
          Rectangle bounds = mySizeInfo.tabs[i];
          g.setColor(mySelectionTab == i
                     ? JBUI.CurrentTheme.ToolWindow.tabSelectedBackground()
                     : JBUI.CurrentTheme.ToolWindow.tabHoveredBackground());
          g.fillRect(x + bounds.x, 0, bounds.width, height);
          g.setColor(getForeground());
        }

        g.drawString(myTabs.get(i).compute(), x + mySizeInfo.tabTitleX[i], tabTitleY);
      }
    }

    @Override
    public void setBounds(int x, int y, int width, int height) {
      myBaselineY = y;
      super.setBounds(x, 0, width, height += y);

      if (myBreadcrumbs == null) {
        myBreadcrumbs = UIUtil.findComponentOfType((JComponent)getParent(), Breadcrumbs.class);
      }

      calculateSize();

      Dimension size = myToolbarComponent.getPreferredSize();
      int toolbarX = getStartX() + mySizeInfo.toolbarX;
      int toolbarY = (height - size.height) / 2;
      myToolbarComponent.setBounds(toolbarX, toolbarY, size.width, size.height);
    }

    private int getStartX() {
      return (getParent().getWidth() - mySizeInfo.width) / 2 - getX();
    }

    @Override
    public Dimension getPreferredSize() {
      calculateSize();
      return new Dimension(mySizeInfo.width, JBUI.scale(30));
    }

    private void calculateSize() {
      if (mySizeInfo != null) {
        return;
      }

      mySizeInfo = new SizeInfo();

      int size = myTabs.size();
      mySizeInfo.tabs = new Rectangle[size];
      mySizeInfo.tabTitleX = new int[size];

      int offset = JBUI.scale(22);
      int x = 0;
      FontMetrics fm = getFontMetrics(getFont());

      for (int i = 0; i < size; i++) {
        int tabWidth = offset + SwingUtilities2.stringWidth(null, fm, myTabs.get(i).compute()) + offset;
        mySizeInfo.tabTitleX[i] = x + offset;
        mySizeInfo.tabs[i] = new Rectangle(x, 0, tabWidth, -1);
        x += tabWidth;
      }

      Dimension toolbarSize = myToolbarComponent.getPreferredSize();
      x += JBUI.scale(10);
      mySizeInfo.width = x + toolbarSize.width;
      mySizeInfo.toolbarX = x;
    }
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

  private static class SizeInfo {
    public int width;

    public Rectangle[] tabs;
    public int[] tabTitleX;

    public int toolbarX;
  }

  private static class PluginsGroupComponent extends JBPanelWithEmptyText {
    private final EventHandler myEventHandler;
    private final LinkListener<IdeaPluginDescriptor> myListener;
    private final LinkListener<String> mySearchListener;
    private final Function<IdeaPluginDescriptor, CellPluginComponent> myFunction;
    private final List<UIPluginGroup> myGroups = new ArrayList<>();

    public PluginsGroupComponent(@NotNull LayoutManager layout,
                                 @NotNull EventHandler eventHandler,
                                 @NotNull LinkListener<IdeaPluginDescriptor> listener,
                                 @NotNull LinkListener<String> searchListener,
                                 @NotNull Function<IdeaPluginDescriptor, CellPluginComponent> function) {
      super(layout);
      myEventHandler = eventHandler;
      myListener = listener;
      mySearchListener = searchListener;
      myFunction = function;

      myEventHandler.connect(this);

      setOpaque(true);
      setBackground(MAIN_BG_COLOR);
    }

    public void clear() {
      myGroups.clear();
      myEventHandler.clear();
      removeAll();
    }

    public void setSelection(@NotNull CellPluginComponent component) {
      myEventHandler.setSelection(component);
    }

    public void addGroup(@NotNull PluginsGroup group) {
      addGroup(group, -1);
    }

    public void addGroup(@NotNull PluginsGroup group, int groupIndex) {
      UIPluginGroup uiGroup = new UIPluginGroup();
      group.ui = uiGroup;
      myGroups.add(groupIndex == -1 ? myGroups.size() : groupIndex, uiGroup);

      OpaquePanel panel = new OpaquePanel(new BorderLayout(), new JBColor(0xF7F7F7, 0x3D3F41));
      panel.setBorder(JBUI.Borders.empty(4, 13));

      JLabel title = new JLabel(group.title);
      title.setForeground(new JBColor(0x787878, 0x999999));
      panel.add(title, BorderLayout.WEST);
      group.titleLabel = title;

      if (group.rightAction != null) {
        panel.add(group.rightAction, BorderLayout.EAST);
      }

      int index;

      if (groupIndex == 0) {
        add(panel, 0);
        index = 1;
      }
      else if (groupIndex == -1) {
        add(panel);
        index = -1;
      }
      else {
        Component anchorPanel = myGroups.get(groupIndex + 1).panel;
        int components = getComponentCount();
        index = -1;

        for (int i = 0; i < components; i++) {
          if (getComponent(i) == anchorPanel) {
            index = i;
            break;
          }
        }

        assert index != -1;
        add(panel, index++);
      }

      uiGroup.panel = panel;

      for (IdeaPluginDescriptor descriptor: group.descriptors) {
        CellPluginComponent pluginComponent = myFunction.fun(descriptor);
        uiGroup.plugins.add(pluginComponent);
        add(pluginComponent, index);
        myEventHandler.addCell(pluginComponent, index);
        pluginComponent.setListeners(myListener, mySearchListener, myEventHandler);
        if (index != -1) {
          index++;
        }
      }
    }

    public void addToGroup(@NotNull PluginsGroup group, @NotNull IdeaPluginDescriptor descriptor) {
      int index = group.addWithIndex(descriptor);
      CellPluginComponent anchor = null;
      int uiIndex = -1;

      if (index == group.ui.plugins.size()) {
        int groupIndex = myGroups.indexOf(group.ui);
        if (groupIndex < myGroups.size() - 1) {
          UIPluginGroup nextGroup = myGroups.get(groupIndex + 1);
          anchor = nextGroup.plugins.get(0);
          uiIndex = getComponentIndex(nextGroup.panel);
        }
      }
      else {
        anchor = group.ui.plugins.get(index);
        uiIndex = getComponentIndex(anchor);
      }

      CellPluginComponent pluginComponent = myFunction.fun(descriptor);
      group.ui.plugins.add(index, pluginComponent);
      add(pluginComponent, uiIndex);
      myEventHandler.addCell(pluginComponent, anchor);
      pluginComponent.setListeners(myListener, mySearchListener, myEventHandler);
    }

    public void removeGroup(@NotNull PluginsGroup group) {
      myGroups.remove(group.ui);
      remove(group.ui.panel);

      for (CellPluginComponent plugin: group.ui.plugins) {
        remove(plugin);
        myEventHandler.removeCell(plugin);
      }

      group.ui = null;
      group.descriptors.clear();
      group.titleLabel = null;
      group.rightAction = null;
    }

    public void removeFromGroup(@NotNull PluginsGroup group, @NotNull IdeaPluginDescriptor descriptor) {
      int index = ContainerUtil.indexOf(group.ui.plugins, (Condition<CellPluginComponent>)component -> component.myPlugin == descriptor);
      assert index != -1;
      CellPluginComponent component = group.ui.plugins.remove(index);
      remove(component);
      myEventHandler.removeCell(component);
      group.descriptors.remove(descriptor);
    }

    private int getComponentIndex(@NotNull Component component) {
      int components = getComponentCount();
      for (int i = 0; i < components; i++) {
        if (getComponent(i) == component) {
          return i;
        }
      }
      return -1;
    }

    public void initialSelection() {
      //noinspection SSBasedInspection
      SwingUtilities.invokeLater(() -> {
        myEventHandler.initialSelection();
        if (getComponentCount() > 0) {
          scrollRectToVisible(getComponent(0).getBounds());
        }
      });
    }
  }

  private static class PluginsGroupComponentWithProgress extends PluginsGroupComponent {
    private AsyncProcessIcon myIcon = new AsyncProcessIcon.Big("Loading") {
      @NotNull
      @Override
      protected Rectangle calculateBounds(@NotNull JComponent container) {
        Dimension size = container.getSize();
        Dimension iconSize = getPreferredSize();
        return new Rectangle((size.width - iconSize.width) / 2, (size.height - iconSize.height) / 2, iconSize.width, iconSize.height);
      }
    };

    public PluginsGroupComponentWithProgress(@NotNull LayoutManager layout,
                                             @NotNull EventHandler eventHandler,
                                             @NotNull LinkListener<IdeaPluginDescriptor> listener,
                                             @NotNull LinkListener<String> searchListener,
                                             @NotNull Function<IdeaPluginDescriptor, CellPluginComponent> function) {
      super(layout, eventHandler, listener, searchListener, function);
      myIcon.setOpaque(false);
      myIcon.setPaintPassiveIcon(false);
      add(myIcon);
      myIcon.resume();
    }

    @Override
    public void removeNotify() {
      super.removeNotify();
      if (myIcon != null && ScreenUtil.isStandardAddRemoveNotify(this)) {
        dispose();
      }
    }

    @Override
    public void doLayout() {
      super.doLayout();
      if (myIcon != null) {
        myIcon.updateLocation(this);
      }
    }

    @Override
    public void paint(Graphics g) {
      super.paint(g);
      if (myIcon != null) {
        myIcon.updateLocation(this);
      }
    }

    public void stopLoading() {
      if (myIcon != null) {
        myIcon.suspend();
        myIcon.setVisible(false);
        dispose();
        doLayout();
        revalidate();
        repaint();
      }
    }

    private void dispose() {
      remove(myIcon);
      Disposer.dispose(myIcon);
      myIcon = null;
    }
  }

  private static class PanelWithProgress extends JBPanelWithEmptyText {
    private AsyncProcessIcon myIcon = new AsyncProcessIcon.Big("Panel.Loading") {
      @NotNull
      @Override
      protected Rectangle calculateBounds(@NotNull JComponent container) {
        Dimension size = container.getSize();
        Dimension iconSize = getPreferredSize();
        return new Rectangle((size.width - iconSize.width) / 2, (size.height - iconSize.height) / 2, iconSize.width, iconSize.height);
      }
    };

    public PanelWithProgress(@NotNull String emptyText) {
      getEmptyText().setText(emptyText);
    }

    public void addProgress() {
      myIcon.setOpaque(false);
      myIcon.setPaintPassiveIcon(false);
      add(myIcon);

      stopLoading();
    }

    @Override
    public void removeNotify() {
      super.removeNotify();
      if (myIcon != null && ScreenUtil.isStandardAddRemoveNotify(this)) {
        remove(myIcon);
        Disposer.dispose(myIcon);
        myIcon = null;
      }
    }

    @Override
    public void doLayout() {
      super.doLayout();
      if (myIcon != null) {
        myIcon.updateLocation(this);
      }
    }

    @Override
    public void paint(Graphics g) {
      super.paint(g);
      if (myIcon != null) {
        myIcon.updateLocation(this);
      }
    }

    public void startLoading() {
      if (myIcon != null) {
        myIcon.setVisible(true);
        myIcon.resume();
        doLayout();
        revalidate();
        repaint();
      }
    }

    public void stopLoading() {
      if (myIcon != null) {
        myIcon.suspend();
        myIcon.setVisible(false);
        doLayout();
        revalidate();
        repaint();
      }
    }

    public void scrollToBegin() {
      //noinspection SSBasedInspection
      SwingUtilities.invokeLater(() -> {
        if (getComponentCount() > 0) {
          scrollRectToVisible(getComponent(0).getBounds());
        }
      });
    }
  }

  private static class EventHandler {
    public static final EventHandler EMPTY = new EventHandler();

    public void connect(@NotNull PluginsGroupComponent container) {
    }

    public void addCell(@NotNull CellPluginComponent component, int index) {
    }

    public void addCell(@NotNull CellPluginComponent component, @Nullable CellPluginComponent anchor) {
    }

    public void removeCell(@NotNull CellPluginComponent component) {
    }

    public void add(@NotNull Component component) {
    }

    public void addAll(@NotNull Component component) {
    }

    public void updateHover(@NotNull CellPluginComponent component) {
    }

    public void initialSelection() {
    }

    public void setSelection(@NotNull CellPluginComponent component) {
    }

    public void clear() {
    }
  }

  private static class MultiSelectionEventHandler extends EventHandler {
    private PluginsGroupComponent myContainer;
    private PluginsListLayout myLayout;
    private List<CellPluginComponent> myComponents;

    private CellPluginComponent myHoverComponent;

    private int mySelectionIndex;
    private int mySelectionLength;

    private final MouseAdapter myMouseHandler;
    private final KeyListener myKeyListener;
    private final FocusListener myFocusListener;

    private final ShortcutSet mySelectAllKeys;
    private boolean myAllSelected;
    private boolean myMixSelection;

    public MultiSelectionEventHandler() {
      clear();

      myMouseHandler = new MouseAdapter() {
        @Override
        public void mouseClicked(MouseEvent event) {
          if (SwingUtilities.isLeftMouseButton(event)) {
            CellPluginComponent component = CellPluginComponent.get(event);
            int index = getIndex(component);

            if (event.isShiftDown()) {
              int end = mySelectionIndex + mySelectionLength + (mySelectionLength > 0 ? -1 : 1);
              if (index != end) {
                moveOrResizeSelection(index < end, false, Math.abs(end - index));
              }
            }
            else if (event.isMetaDown()) {
              myMixSelection = true;
              myAllSelected = false;
              mySelectionIndex = index;
              mySelectionLength = 1;
              component.setSelection(component.getSelection() == SelectionType.SELECTION
                                     ? SelectionType.NONE : SelectionType.SELECTION, true);
            }
            else {
              clearSelectionWithout(index);
              singleSelection(component, index);
            }
          }
          else if (SwingUtilities.isRightMouseButton(event)) {
            CellPluginComponent component = CellPluginComponent.get(event);

            if (myAllSelected || myMixSelection) {
              int size = getSelection().size();
              if (size == 0) {
                singleSelection(component, getIndex(component));
              }
              else if (size == 1) {
                ensureMoveSingleSelection(component);
              }
            }
            else if (mySelectionLength == 0 || mySelectionLength == 1) {
              ensureMoveSingleSelection(component);
            }

            DefaultActionGroup group = new DefaultActionGroup();
            component.createPopupMenu(group, getSelection());
            if (group.getChildrenCount() == 0) {
              return;
            }

            ActionPopupMenu popupMenu = ActionManager.getInstance().createActionPopupMenu(ActionPlaces.UNKNOWN, group);
            popupMenu.setTargetComponent(component);
            popupMenu.getComponent().show(event.getComponent(), event.getX(), event.getY());
            event.consume();
          }
        }

        @Override
        public void mouseExited(MouseEvent event) {
          if (myHoverComponent != null) {
            if (myHoverComponent.getSelection() == SelectionType.HOVER) {
              myHoverComponent.setSelection(SelectionType.NONE);
            }
            myHoverComponent = null;
          }
        }

        @Override
        public void mouseMoved(MouseEvent event) {
          if (myHoverComponent == null) {
            CellPluginComponent component = CellPluginComponent.get(event);
            if (component.getSelection() == SelectionType.NONE) {
              myHoverComponent = component;
              component.setSelection(SelectionType.HOVER);
            }
          }
        }
      };

      mySelectAllKeys = getShortcuts(IdeActions.ACTION_SELECT_ALL);

      myKeyListener = new KeyAdapter() {
        @Override
        public void keyPressed(KeyEvent event) {
          int code = event.getKeyCode();
          int modifiers = event.getModifiers();
          KeyboardShortcut shortcut = new KeyboardShortcut(KeyStroke.getKeyStroke(code, modifiers), null);

          if (check(shortcut, mySelectAllKeys)) {
            event.consume();
            selectAll();
            return;
          }

          if (code == KeyEvent.VK_HOME || code == KeyEvent.VK_END) {
            if (myComponents.isEmpty()) {
              return;
            }
            event.consume();
            if (event.isShiftDown()) {
              moveOrResizeSelection(code == KeyEvent.VK_HOME, false, 2 * myComponents.size());
            }
            else {
              int index = code == KeyEvent.VK_HOME ? 0 : myComponents.size() - 1;
              clearSelectionWithout(index);
              singleSelection(index);
            }
          }
          else if (code == KeyEvent.VK_UP || code == KeyEvent.VK_DOWN) {
            event.consume();
            if (modifiers == 0) {
              moveOrResizeSelection(code == KeyEvent.VK_UP, true, 1);
            }
            else if (modifiers == Event.SHIFT_MASK) {
              moveOrResizeSelection(code == KeyEvent.VK_UP, false, 1);
            }
          }
          else if (code == KeyEvent.VK_PAGE_UP || code == KeyEvent.VK_PAGE_DOWN) {
            if (myComponents.isEmpty()) {
              return;
            }

            event.consume();
            int pageCount = myContainer.getVisibleRect().height / myLayout.myLineHeight;
            moveOrResizeSelection(code == KeyEvent.VK_PAGE_UP, !event.isShiftDown(), pageCount);
          }
          else if (code == KeyEvent.VK_SPACE || code == KeyEvent.VK_ENTER || code == KeyEvent.VK_BACK_SPACE) {
            assert mySelectionLength != 0;
            CellPluginComponent component = myComponents.get(mySelectionIndex);
            if (component.getSelection() != SelectionType.SELECTION) {
              component.setSelection(SelectionType.SELECTION);
            }
            component.handleKeyAction(code, getSelection());
          }
        }
      };

      myFocusListener = new FocusAdapter() {
        @Override
        public void focusGained(FocusEvent event) {
          if (mySelectionIndex >= 0 && mySelectionLength == 1 && !myMixSelection) {
            CellPluginComponent component = CellPluginComponent.get(event);
            int index = getIndex(component);
            if (mySelectionIndex != index) {
              clearSelectionWithout(index);
              singleSelection(component, index);
            }
          }
        }
      };
    }

    @Nullable
    private static ShortcutSet getShortcuts(@NotNull String id) {
      AnAction action = ActionManager.getInstance().getAction(id);
      return action == null ? null : action.getShortcutSet();
    }

    private static boolean check(@NotNull KeyboardShortcut shortcut, @Nullable ShortcutSet set) {
      if (set != null) {
        for (Shortcut test: set.getShortcuts()) {
          if (test.isKeyboard() && shortcut.startsWith(test)) {
            return true;
          }
        }
      }
      return false;
    }

    @Override
    public void connect(@NotNull PluginsGroupComponent container) {
      myContainer = container;
      myLayout = (PluginsListLayout)container.getLayout();
    }

    @Override
    public void addCell(@NotNull CellPluginComponent component, int index) {
      if (index == -1) {
        myComponents.add(component);
      }
      else {
        myComponents.add(index, component);
      }
    }

    @Override
    public void addCell(@NotNull CellPluginComponent component, @Nullable CellPluginComponent anchor) {
      if (anchor == null) {
        myComponents.add(component);
      }
      else {
        myComponents.add(myComponents.indexOf(anchor), component);
      }
    }

    @Override
    public void removeCell(@NotNull CellPluginComponent component) {
      myComponents.remove(component);
    }

    @Override
    public void initialSelection() {
      if (!myComponents.isEmpty()) {
        singleSelection(0);
      }
    }

    @NotNull
    private List<CellPluginComponent> getSelection() {
      List<CellPluginComponent> selection = new ArrayList<>();

      for (CellPluginComponent component: myComponents) {
        if (component.getSelection() == SelectionType.SELECTION) {
          selection.add(component);
        }
      }

      return selection;
    }

    @Override
    public void clear() {
      myComponents = new ArrayList<>();
      myHoverComponent = null;
      mySelectionIndex = -1;
      mySelectionLength = 0;
      myAllSelected = false;
      myMixSelection = false;
    }

    private void selectAll() {
      if (myAllSelected) {
        return;
      }

      myAllSelected = true;
      myMixSelection = false;
      myHoverComponent = null;

      for (CellPluginComponent component: myComponents) {
        if (component.getSelection() != SelectionType.SELECTION) {
          component.setSelection(SelectionType.SELECTION, false);
        }
      }
    }

    private void moveOrResizeSelection(boolean up, boolean singleSelection, int count) {
      if (singleSelection) {
        assert mySelectionLength != 0;
        int index;
        if (mySelectionLength > 0) {
          index = up
                  ? Math.max(mySelectionIndex + mySelectionLength - 1 - count, 0)
                  : Math.min(mySelectionIndex + mySelectionLength - 1 + count, myComponents.size() - 1);
        }
        else {
          index = up
                  ? Math.max(mySelectionIndex + mySelectionLength + 1 - count, 0)
                  : Math.min(mySelectionIndex + mySelectionLength + 1 + count, myComponents.size() - 1);
        }
        clearSelectionWithout(index);
        singleSelection(index);
      }
      // multi selection
      else if (up) {
        if (mySelectionLength > 0) {
          if (mySelectionIndex + mySelectionLength - 1 > 0) {
            clearAllOrMixSelection();
            for (int i = 0; i < count && mySelectionIndex + mySelectionLength - 1 > 0; i++) {
              mySelectionLength--;
              if (mySelectionLength > 0) {
                myComponents.get(mySelectionIndex + mySelectionLength).setSelection(SelectionType.NONE, true);
              }
              if (mySelectionLength == 0) {
                myComponents.get(mySelectionIndex - 1).setSelection(SelectionType.SELECTION);
                mySelectionLength = -2;
                int newCount = count - i - 1;
                if (newCount > 0) {
                  moveOrResizeSelection(true, false, newCount);
                }
                return;
              }
            }
          }
        }
        else if (mySelectionIndex + mySelectionLength + 1 > 0) {
          clearAllOrMixSelection();
          for (int i = 0, index = mySelectionIndex + mySelectionLength + 1; i < count && index > 0; i++, index--) {
            mySelectionLength--;
            myComponents.get(index - 1).setSelection(SelectionType.SELECTION);
          }
        }
      }
      // down
      else if (mySelectionLength > 0) {
        if (mySelectionIndex + mySelectionLength < myComponents.size()) {
          clearAllOrMixSelection();
          for (int i = 0, index = mySelectionIndex + mySelectionLength, size = myComponents.size();
               i < count && index < size;
               i++, index++) {
            myComponents.get(index).setSelection(SelectionType.SELECTION);
            mySelectionLength++;
          }
        }
      }
      else {
        clearAllOrMixSelection();
        for (int i = 0; i < count; i++) {
          mySelectionLength++;
          myComponents.get(mySelectionIndex + mySelectionLength).setSelection(SelectionType.NONE, true);
          if (mySelectionLength == -1) {
            mySelectionLength = 1;
            int newCount = count - i - 1;
            if (newCount > 0) {
              moveOrResizeSelection(false, false, newCount);
            }
            return;
          }
        }
      }
    }

    private int getIndex(@NotNull CellPluginComponent component) {
      int index = myComponents.indexOf(component);
      assert index >= 0 : component;
      return index;
    }

    private void clearAllOrMixSelection() {
      if (!myAllSelected && !myMixSelection) {
        return;
      }
      if (myMixSelection && mySelectionIndex != -1) {
        CellPluginComponent component = myComponents.get(mySelectionIndex);
        if (component.getSelection() != SelectionType.SELECTION) {
          component.setSelection(SelectionType.SELECTION);
        }
      }
      myAllSelected = false;
      myMixSelection = false;

      int first;
      int last;

      if (mySelectionLength > 0) {
        first = mySelectionIndex;
        last = mySelectionIndex + mySelectionLength;
      }
      else {
        first = mySelectionIndex + mySelectionLength + 1;
        last = mySelectionIndex + 1;
      }

      for (int i = 0; i < first; i++) {
        CellPluginComponent component = myComponents.get(i);
        if (component.getSelection() == SelectionType.SELECTION) {
          component.setSelection(SelectionType.NONE);
        }
      }
      for (int i = last, size = myComponents.size(); i < size; i++) {
        CellPluginComponent component = myComponents.get(i);
        if (component.getSelection() == SelectionType.SELECTION) {
          component.setSelection(SelectionType.NONE);
        }
      }
    }

    private void clearSelectionWithout(int withoutIndex) {
      myAllSelected = false;
      myMixSelection = false;
      for (int i = 0, size = myComponents.size(); i < size; i++) {
        if (i != withoutIndex) {
          CellPluginComponent component = myComponents.get(i);
          if (component.getSelection() == SelectionType.SELECTION) {
            component.setSelection(SelectionType.NONE);
          }
        }
      }
    }

    private void ensureMoveSingleSelection(CellPluginComponent component) {
      int index = getIndex(component);
      if (mySelectionLength == 0 || mySelectionIndex != index) {
        clearSelectionWithout(index);
        singleSelection(component, index);
      }
    }

    @Override
    public void setSelection(@NotNull CellPluginComponent component) {
      clearSelectionWithout(-1);
      singleSelection(component, getIndex(component));
    }

    private void singleSelection(int index) {
      singleSelection(myComponents.get(index), index);
    }

    private void singleSelection(@NotNull CellPluginComponent component, int index) {
      mySelectionIndex = index;
      mySelectionLength = 1;
      if (myHoverComponent == component) {
        myHoverComponent = null;
      }
      if (component.getSelection() != SelectionType.SELECTION) {
        component.setSelection(SelectionType.SELECTION);
      }
    }

    @Override
    public void add(@NotNull Component component) {
      component.addMouseListener(myMouseHandler);
      component.addMouseMotionListener(myMouseHandler);
      component.addKeyListener(myKeyListener);
      component.addFocusListener(myFocusListener);
    }

    @Override
    public void addAll(@NotNull Component component) {
      add(component);
      for (Component child: uiChildren(component)) {
        addAll(child);
      }
    }

    @Override
    public void updateHover(@NotNull CellPluginComponent component) {
      ApplicationManager.getApplication().invokeLater(() -> {
        myHoverComponent = component;
        if (component.getSelection() == SelectionType.NONE) {
          component.setSelection(SelectionType.HOVER);
        }
      }, ModalityState.any());
    }
  }

  private static class PluginsListLayout extends AbstractLayoutManager {
    private int myLineHeight;

    @Override
    public Dimension preferredLayoutSize(@NotNull Container parent) {
      calculateLineHeight(parent);

      List<UIPluginGroup> groups = ((PluginsGroupComponent)parent).myGroups;
      int height = 0;

      for (UIPluginGroup group: groups) {
        height += group.panel.getPreferredSize().height;
        height += group.plugins.size() * myLineHeight;
      }

      return new Dimension(0, height);
    }

    @Override
    public void layoutContainer(@NotNull Container parent) {
      calculateLineHeight(parent);

      List<UIPluginGroup> groups = ((PluginsGroupComponent)parent).myGroups;
      int width = parent.getWidth();
      int y = 0;

      for (UIPluginGroup group: groups) {
        Component component = group.panel;
        int height = component.getPreferredSize().height;
        component.setBounds(0, y, width, height);
        y += height;

        for (CellPluginComponent plugin: group.plugins) {
          plugin.setBounds(0, y, width, myLineHeight);
          y += myLineHeight;
        }
      }
    }

    private void calculateLineHeight(@NotNull Container parent) {
      List<UIPluginGroup> groups = ((PluginsGroupComponent)parent).myGroups;
      int width = getParentWidth(parent) - parent.getInsets().right;

      myLineHeight = 0;

      for (UIPluginGroup group: groups) {
        for (CellPluginComponent plugin: group.plugins) {
          JEditorPane description = plugin.myDescription;
          if (description != null) {
            plugin.doLayout();
            int parentWidth = width - SwingUtilities.convertPoint(description.getParent(), description.getLocation(), plugin).x;
            if (parentWidth > 0) {
              description.putClientProperty("parent.width", new Integer(parentWidth));
            }
          }

          plugin.doLayout();
          myLineHeight = Math.max(myLineHeight, plugin.getPreferredSize().height);
        }
      }
    }
  }

  private static class PluginsGridLayout extends AbstractLayoutManager {
    private final int myFirstVOffset = JBUI.scale(10);
    private final int myMiddleVOffset = JBUI.scale(20);
    private final int myLastVOffset = JBUI.scale(30);
    private final int myMiddleHOffset = JBUI.scale(20);

    private final Dimension myCellSize = new Dimension();

    @Override
    public Dimension preferredLayoutSize(@NotNull Container parent) {
      calculateCellSize(parent);

      int width = getParentWidth(parent);
      int cellWidth = myCellSize.width;
      int columns = width / (cellWidth + myMiddleHOffset);

      if (columns < 2) {
        columns = 2;
      }
      width = columns * (cellWidth + myMiddleHOffset) - myMiddleHOffset;

      int height = 0;
      int cellHeight = myCellSize.height;
      List<UIPluginGroup> groups = ((PluginsGroupComponent)parent).myGroups;

      for (UIPluginGroup group: groups) {
        height += group.panel.getPreferredSize().height;

        int plugins = group.plugins.size();
        int rows;

        if (plugins <= columns) {
          rows = 1;
        }
        else {
          rows = plugins / columns;
          if (plugins > rows * columns) {
            rows++;
          }
        }

        height += myFirstVOffset + rows * (cellHeight + myMiddleVOffset) - myMiddleVOffset + myLastVOffset;
      }

      return new Dimension(width, height);
    }

    @Override
    public void layoutContainer(@NotNull Container parent) {
      calculateCellSize(parent);

      List<UIPluginGroup> groups = ((PluginsGroupComponent)parent).myGroups;
      int width = parent.getWidth();
      int y = 0;
      int columns = Math.max(1, width / (myCellSize.width + myMiddleHOffset));

      for (UIPluginGroup group: groups) {
        Component component = group.panel;
        int height = component.getPreferredSize().height;
        component.setBounds(0, y, width, height);
        y += height + myFirstVOffset;
        y += layoutPlugins(group.plugins, y, columns);
      }
    }

    private void calculateCellSize(@NotNull Container parent) {
      myCellSize.width = 0;
      myCellSize.height = 0;

      List<UIPluginGroup> groups = ((PluginsGroupComponent)parent).myGroups;

      for (UIPluginGroup group: groups) {
        for (CellPluginComponent plugin: group.plugins) {
          plugin.doLayout();
          Dimension size = plugin.getPreferredSize();
          myCellSize.width = Math.max(myCellSize.width, size.width);
          myCellSize.height = Math.max(myCellSize.height, size.height);
        }
      }
    }

    private int layoutPlugins(@NotNull List<CellPluginComponent> plugins, int startY, int columns) {
      int x = 0;
      int y = 0;
      int width = myCellSize.width;
      int height = myCellSize.height;
      int column = 0;

      for (int i = 0, size = plugins.size(), last = size - 1; i < size; i++) {
        plugins.get(i).setBounds(x, startY + y, width, height);
        x += width + myMiddleHOffset;

        if (++column == columns || i == last) {
          x = 0;
          y += height + myMiddleVOffset;
          column = 0;
        }
      }

      y += (myLastVOffset - myMiddleVOffset);

      return y;
    }
  }

  private static int getParentWidth(@NotNull Container parent) {
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

  private void hideSearchPopup() {
    if (mySearchPopup != null) {
      mySearchPopup.cancel();
      mySearchPopup = null;
      mySearchPopupModel = null;
      mySearchPopupList = null;
      mySearchFlyResult = null;
    }
  }

  private void setSearchTextIgnoreEvents(@NotNull String text) {
    try {
      mySkipDocumentEvents = true;
      mySearchTextField.setText(text);
    }
    finally {
      mySkipDocumentEvents = false;
    }
  }

  private class SearchResultInfo {
    public final String key;
    public int backTabIndex;

    private String myQuery;

    private final PanelWithProgress myPanel;

    private final PluginsGroupComponent myInstalledPanel =
      new PluginsGroupComponent(new PluginsListLayout(), new MultiSelectionEventHandler(), myNameListener, mySearchListener,
                                descriptor -> new ListPluginComponent(myPluginsModel, descriptor, false));
    private PluginsGroup myInstalledGroup;

    private final PluginsGroupComponent myRepositoryPanel =
      new PluginsGroupComponent(new PluginsGridLayout(), EventHandler.EMPTY, myNameListener, mySearchListener,
                                descriptor -> new GridCellPluginComponent(myPluginsModel, descriptor, myTagBuilder));
    private PluginsGroup myRepositoryGroup;

    private AtomicBoolean myRunQuery;

    public SearchResultInfo(@NotNull String query) {
      key = query;

      myPanel = new PanelWithProgress("Nothing to show");
      myPanel.setOpaque(true);
      myPanel.setBackground(MAIN_BG_COLOR);

      myPanel.setLayout(new AbstractLayoutManager() {
        @Override
        public Dimension preferredLayoutSize(Container parent) {
          Dimension size = new Dimension();
          if (myInstalledPanel.isVisible()) {
            size.setSize(myInstalledPanel.getPreferredSize());
          }
          if (myRepositoryPanel.isVisible()) {
            Dimension secondSize = myRepositoryPanel.getPreferredSize();
            size.width = Math.max(size.width, secondSize.width);
            size.height += secondSize.height;
          }
          return size.height > 0 ? size : new Dimension(-1, -1);
        }

        @Override
        public void layoutContainer(Container parent) {
          int width = parent.getWidth();
          int y = 0;
          if (myInstalledPanel.isVisible()) {
            int childHeight = myInstalledPanel.getPreferredSize().height;
            myInstalledPanel.setBounds(0, y, width, childHeight);
            y = childHeight;
          }
          if (myRepositoryPanel.isVisible()) {
            int childHeight = myRepositoryPanel.getPreferredSize().height;
            myRepositoryPanel.setBounds(0, y, width, childHeight);
          }
        }
      });

      myPanel.add(myInstalledPanel);
      myPanel.add(myRepositoryPanel);
      myPanel.addProgress();

      myInstalledPanel.setVisible(false);
      myRepositoryPanel.setVisible(false);

      handleQuery(query);
    }

    @NotNull
    public JComponent createScrollPane() {
      JBScrollPane pane = new JBScrollPane(myPanel);
      pane.setBorder(JBUI.Borders.empty());
      return pane;
    }

    public void setQuery(@NotNull String query) {
      if (query.equals(myQuery)) {
        return;
      }

      if (myRunQuery != null) {
        myRunQuery.set(false);
        myRunQuery = null;

        myPanel.stopLoading();
      }
      if (myInstalledGroup != null) {
        removeGroup(myInstalledPanel, myInstalledGroup);
        myInstalledGroup = null;
      }
      if (myRepositoryGroup != null) {
        removeGroup(myRepositoryPanel, myRepositoryGroup);
        myRepositoryGroup = null;
      }

      handleQuery(query);
    }

    private void handleQuery(@NotNull String query) {
      myQuery = query;

      String serverQuery = recognizeServerSearchQuery(query);

      if (serverQuery == null) {
        Pair<PluginsGroup, PluginsGroup> groups = localSearchPlugins(query);

        myInstalledGroup = groups.first;
        if (myInstalledGroup != null) {
          myInstalledPanel.addGroup(myInstalledGroup);
          myInstalledGroup.titleWithCount();
          myInstalledPanel.setVisible(true);
          myInstalledPanel.initialSelection();
        }

        myRepositoryGroup = groups.second;
        if (myRepositoryGroup != null) {
          myRepositoryPanel.addGroup(myRepositoryGroup);
          myRepositoryGroup.titleWithCount();
          myRepositoryPanel.setVisible(true);
        }

        if (myInstalledGroup != null || myRepositoryGroup != null) {
          myPanel.scrollToBegin();
        }

        myPanel.doLayout();
      }
      else {
        myPanel.startLoading();

        AtomicBoolean runQuery = myRunQuery = new AtomicBoolean(true);

        ApplicationManager.getApplication().executeOnPooledThread(() -> {
          PluginsGroup group = new PluginsGroup("Search Result");

          try {
            Map<String, IdeaPluginDescriptor> jbRepositoryMap = loadJBRepository();

            for (String pluginId: requestToPluginRepository(createSearchUrl(serverQuery, 10000), forceHttps())) {
              IdeaPluginDescriptor descriptor = jbRepositoryMap.get(pluginId);
              if (descriptor != null) {
                group.descriptors.add(descriptor);
              }
            }
          }
          catch (IOException e) {
            PluginManagerMain.LOG.info(e);
          }
          finally {
            ApplicationManager.getApplication().invokeLater(() -> {
              if (!runQuery.get()) {
                return;
              }
              myRunQuery = null;

              myPanel.stopLoading();

              if (!group.descriptors.isEmpty()) {
                myRepositoryPanel.addGroup(group);
                group.titleWithCount();
                myRepositoryGroup = group;
                myRepositoryPanel.setVisible(true);
                myPanel.scrollToBegin();
              }

              myPanel.doLayout();
            }, ModalityState.any());
          }
        });
      }
    }

    public void close() {
      if (myRunQuery != null) {
        myRunQuery.set(false);
        myRunQuery = null;
      }

      if (myInstalledGroup != null) {
        closeGroup(myInstalledGroup);
        myInstalledGroup = null;
      }
      if (myRepositoryGroup != null) {
        closeGroup(myRepositoryGroup);
        myRepositoryGroup = null;
      }
    }

    private void removeGroup(@NotNull PluginsGroupComponent panel, @NotNull PluginsGroup group) {
      closeGroup(group);
      panel.removeGroup(group);
      panel.setVisible(false);
    }

    private void closeGroup(@NotNull PluginsGroup group) {
      if (group.ui == null) {
        return;
      }
      for (CellPluginComponent component: group.ui.plugins) {
        component.close();
      }
    }

    public void setText() {
      setSearchTextIgnoreEvents(myQuery);
    }

    public void clearText() {
      setSearchTextIgnoreEvents("");
    }
  }

  private static class UIPluginGroup {
    public Component panel;
    public List<CellPluginComponent> plugins = new ArrayList<>();
  }

  private static class PluginsGroup {
    protected final String myTitlePrefix;
    public String title;
    public JLabel titleLabel;
    public LinkLabel<Object> rightAction;
    public final List<IdeaPluginDescriptor> descriptors = new ArrayList<>();
    public UIPluginGroup ui;

    public PluginsGroup(@NotNull String title) {
      myTitlePrefix = title;
      this.title = title;
    }

    public void titleWithCount() {
      title = myTitlePrefix + " (" + descriptors.size() + ")";
      updateTitle();
    }

    public void titleWithEnabled(@NotNull MyPluginModel pluginModel) {
      int enabled = 0;
      for (IdeaPluginDescriptor descriptor: descriptors) {
        if (pluginModel.isEnabled(descriptor)) {
          enabled++;
        }
      }
      titleWithCount(enabled);
    }

    public void titleWithCount(int enabled) {
      title = myTitlePrefix + " (" + enabled + " of " + descriptors.size() + " enabled)";
      updateTitle();
    }

    protected void updateTitle() {
      if (titleLabel != null) {
        titleLabel.setText(title);
      }
    }

    public int addWithIndex(@NotNull IdeaPluginDescriptor descriptor) {
      descriptors.add(descriptor);
      sortByName();
      return descriptors.indexOf(descriptor);
    }

    public void sortByName() {
      ContainerUtil.sort(descriptors, Comparator.comparing(IdeaPluginDescriptor::getName));
    }
  }

  @NotNull
  private static <T extends Component> T installTiny(@NotNull T component) {
    return SystemInfo.isMac ? RelativeFont.TINY.install(component) : component;
  }

  private static abstract class CellPluginComponent extends JPanel {
    private static final Color HOVER_COLOR = new JBColor(0xE9EEF5, 0x464A4D);
    private static final Color GRAY_COLOR = new JBColor(Gray._130, Gray._120);

    protected final IdeaPluginDescriptor myPlugin;

    protected LinkLabel myIconLabel;
    protected LinkLabel myName;
    protected JEditorPane myDescription;
    protected MouseListener myHoverNameListener;

    protected SelectionType mySelection = SelectionType.NONE;

    protected CellPluginComponent(@NotNull IdeaPluginDescriptor plugin) {
      myPlugin = plugin;
    }

    protected void addIconComponent(@NotNull JPanel parent, @Nullable Object constraints) {
      myIconLabel = new LinkLabel(null, AllIcons.Plugins.PluginLogo_40);
      myIconLabel.setVerticalAlignment(SwingConstants.TOP);
      myIconLabel.setOpaque(false);
      parent.add(myIconLabel, constraints);
    }

    protected void addNameComponent(@NotNull JPanel parent) {
      myName = new LinkComponent();
      myName.setText(myPlugin.getName());
      parent.add(RelativeFont.BOLD.install(myName));
    }

    protected void updateIcon(boolean errors, boolean disabled) {
      myIconLabel.setIcon(PluginLogoInfo.getIcon(false, isJBPlugin(myPlugin), errors, disabled));
    }

    protected void addDescriptionComponent(@NotNull JPanel parent, @Nullable String description, @NotNull LineFunction function) {
      if (StringUtil.isEmptyOrSpaces(description)) {
        return;
      }

      myDescription = new JEditorPane() {
        @Override
        public Dimension getPreferredSize() {
          if (getWidth() == 0 || getHeight() == 0) {
            setSize(new JBDimension(180, 20));
          }
          Integer property = (Integer)getClientProperty("parent.width");
          int width = property == null ? JBUI.scale(180) : property;
          View view = getUI().getRootView(this);
          view.setSize(width, Integer.MAX_VALUE);
          return new Dimension(width, function.getHeight(this));
        }

        @Override
        protected void paintComponent(Graphics g) {
          super.paintComponent(g);
          function.paintComponent(this, g);
        }
      };

      myDescription.setEditorKit(new UIUtil.JBWordWrapHtmlEditorKit());
      myDescription.setEditable(false);
      myDescription.setFocusable(false);
      myDescription.setOpaque(false);
      myDescription.setBorder(null);
      myDescription.setText(XmlStringUtil.wrapInHtml(description));

      if (myDescription.getCaret() != null) {
        myDescription.setCaretPosition(0);
      }

      parent.add(installTiny(myDescription));
    }

    @NotNull
    public SelectionType getSelection() {
      return mySelection;
    }

    public void setSelection(@NotNull SelectionType type) {
      setSelection(type, type == SelectionType.SELECTION);
    }

    public void setSelection(@NotNull SelectionType type, boolean scrollAndFocus) {
      mySelection = type;

      if (scrollAndFocus) {
        JComponent parent = (JComponent)getParent();
        if (parent != null) {
          Rectangle bounds = getBounds();
          if (!parent.getVisibleRect().contains(bounds)) {
            parent.scrollRectToVisible(bounds);
          }

          if (type == SelectionType.SELECTION) {
            IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(() -> IdeFocusManager.getGlobalInstance().requestFocus(this, true));
          }
        }
      }

      updateColors(GRAY_COLOR, type == SelectionType.NONE ? MAIN_BG_COLOR : HOVER_COLOR);
      repaint();
    }

    protected void updateColors(@NotNull Color grayedFg, @NotNull Color background) {
      setBackground(background);

      if (myDescription != null) {
        myDescription.setForeground(grayedFg);
      }
    }

    protected void fullRepaint() {
      Container parent = getParent();
      parent.doLayout();
      parent.revalidate();
      parent.repaint();
    }

    public void setListeners(@NotNull LinkListener<IdeaPluginDescriptor> listener,
                             @NotNull LinkListener<String> searchListener,
                             @NotNull EventHandler eventHandler) {
      //noinspection unchecked
      myIconLabel.setListener(listener, myPlugin);
      //noinspection unchecked
      myName.setListener(listener, myPlugin);

      myHoverNameListener = new MouseAdapter() {
        @Override
        public void mouseEntered(MouseEvent event) {
          myName.entered(event);
        }

        @Override
        public void mouseExited(MouseEvent event) {
          myName.exited(event);
        }
      };
      myIconLabel.addMouseListener(myHoverNameListener);
    }

    public void createPopupMenu(@NotNull DefaultActionGroup group, @NotNull List<CellPluginComponent> selection) {
    }

    public void handleKeyAction(int keyCode, @NotNull List<CellPluginComponent> selection) {
    }

    @NotNull
    public static CellPluginComponent get(@NotNull ComponentEvent event) {
      //noinspection ConstantConditions
      return UIUtil.getParentOfType(CellPluginComponent.class, event.getComponent());
    }

    public void close() {
    }
  }

  private static class LineFunction {
    private final int myLines;
    private final boolean myShowDots;
    private Point myLastPoint;

    public LineFunction(int lines, boolean showDots) {
      myLines = lines + 1;
      myShowDots = showDots;
    }

    public void paintComponent(@NotNull JEditorPane pane, @NotNull Graphics g) {
      if (myShowDots && myLastPoint != null) {
        if (g instanceof Graphics2D) {
          ((Graphics2D)g).setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        }
        g.setColor(pane.getForeground());
        g.drawString("...", myLastPoint.x, myLastPoint.y + g.getFontMetrics().getAscent());
      }
    }

    public int getHeight(@NotNull JEditorPane pane) {
      myLastPoint = null;

      try {
        int line = 0;
        int startLineY = -1;
        int length = pane.getDocument().getLength();

        for (int i = 0; i < length; i++) {
          Rectangle r = pane.modelToView(i);
          if (r != null && r.height > 0 && startLineY < r.y) {
            startLineY = r.y;
            if (++line == myLines) {
              int ii = i;
              while (ii > 0) {
                Rectangle rr = pane.modelToView(--ii);
                if (rr != null) {
                  myLastPoint = rr.getLocation();
                  break;
                }
              }
              return r.y;
            }
          }
        }
      }
      catch (BadLocationException ignored) {
      }

      return (int)(pane.getUI().getRootView(pane).getPreferredSpan(View.Y_AXIS) + JBUI.scale(2f));
    }
  }

  private static class ListPluginComponent extends CellPluginComponent {
    private final MyPluginModel myPluginModel;
    private boolean myUninstalled;

    private JLabel myVersion;
    private JLabel myLastUpdated;
    private JButton myUpdateButton;
    private final JCheckBox myEnableDisableButton = new JCheckBox();
    private RestartButton myRestartButton;
    private final BaselinePanel myBaselinePanel = new BaselinePanel();
    private ProgressIndicatorEx myIndicator;

    public ListPluginComponent(@NotNull MyPluginModel pluginModel, @NotNull IdeaPluginDescriptor plugin, boolean pluginForUpdate) {
      super(plugin);
      myPluginModel = pluginModel;
      pluginModel.addComponent(this);

      setFocusable(true);
      myEnableDisableButton.setFocusable(false);

      setOpaque(true);
      setLayout(new BorderLayout(JBUI.scale(8), 0));
      setBorder(JBUI.Borders.empty(5, 10, 10, 10));

      createButtons(pluginForUpdate);

      JPanel westPanel = new NonOpaquePanel(createCheckboxIconLayout());
      westPanel.setBorder(JBUI.Borders.emptyTop(5));
      westPanel.add(myEnableDisableButton);
      addIconComponent(westPanel, null);
      add(westPanel, BorderLayout.WEST);

      JPanel centerPanel = new NonOpaquePanel(new VerticalLayout(0));
      add(centerPanel);

      centerPanel.add(myBaselinePanel, VerticalLayout.FILL_HORIZONTAL);

      addNameComponent(myBaselinePanel);
      myName.setVerticalAlignment(SwingConstants.TOP);

      if (pluginForUpdate) {
        createVersion();
      }
      updateErrors();

      if (pluginForUpdate) {
        addDescriptionComponent(centerPanel, getChangeNotes(plugin), new LineFunction(3, false));
      }
      else {
        addDescriptionComponent(centerPanel, getShortDescription(plugin, false), new LineFunction(1, true));
      }

      if (MyPluginModel.isInstallingOrUpdate(plugin)) {
        showProgress(false);
      }

      setSelection(SelectionType.NONE);
    }

    private void createButtons(boolean update) {
      myEnableDisableButton.setOpaque(false);

      if (myPlugin instanceof IdeaPluginDescriptorImpl && ((IdeaPluginDescriptorImpl)myPlugin).isDeleted()) {
        myRestartButton = new RestartButton(myPluginModel);
        myRestartButton.setFocusable(false);
        myBaselinePanel.addButtonComponent(myRestartButton);

        myEnableDisableButton.setSelected(false);
        myEnableDisableButton.setEnabled(false);

        myUninstalled = true;
      }
      else {
        InstalledPluginsState pluginsState = InstalledPluginsState.getInstance();
        PluginId id = myPlugin.getPluginId();

        if (pluginsState.wasInstalled(id) || pluginsState.wasUpdated(id)) {
          myRestartButton = new RestartButton(myPluginModel);
          myRestartButton.setFocusable(false);
          myBaselinePanel.addButtonComponent(myRestartButton);
        }
        else if (update) {
          myUpdateButton = new UpdateButton();
          myUpdateButton.setFocusable(false);
          myUpdateButton.addActionListener(e -> myPluginModel.installOrUpdatePlugin(myPlugin, false));
          myBaselinePanel.addButtonComponent(myUpdateButton);
        }

        myEnableDisableButton.setSelected(isEnabledState());
        myEnableDisableButton.addActionListener(e -> myPluginModel.changeEnableDisable(myPlugin));
      }
    }

    public void showProgress() {
      showProgress(true);
    }

    private void showProgress(boolean repaint) {
      myEnableDisableButton.setSelected(false);
      myEnableDisableButton.setEnabled(false);

      MyProgressIndicator indicator = new MyProgressIndicator();
      indicator.setCancelRunnable(() -> myPluginModel.finishInstall(myPlugin, false));
      myBaselinePanel.setProgressComponent(this, indicator.createBaselineWrapper());
      myPluginModel.addProgress(myPlugin, indicator);
      myIndicator = indicator;

      if (repaint) {
        fullRepaint();
      }
    }

    public void hideProgress(boolean success) {
      myIndicator = null;
      myEnableDisableButton.setEnabled(true);

      myBaselinePanel.removeProgressComponent();
      if (success) {
        changeUpdateToRestart();
      }
      fullRepaint();
    }

    @NotNull
    private static AbstractLayoutManager createCheckboxIconLayout() {
      return new AbstractLayoutManager() {
        JBValue offset = new JBValue.Float(12);

        @Override
        public Dimension preferredLayoutSize(Container parent) {
          Dimension size = new Dimension();

          if (parent.getComponentCount() == 2) {
            Dimension iconSize = parent.getComponent(1).getPreferredSize();
            size.width = parent.getComponent(0).getPreferredSize().width + offset.get() + iconSize.width;
            size.height = iconSize.height;
          }

          JBInsets.addTo(size, parent.getInsets());
          return size;
        }

        @Override
        public void layoutContainer(Container parent) {
          if (parent.getComponentCount() == 2) {
            Component checkBox = parent.getComponent(0);
            Component icon = parent.getComponent(1);

            Dimension checkBoxSize = checkBox.getPreferredSize();
            Dimension iconSize = icon.getPreferredSize();
            Insets insets = parent.getInsets();
            int x = insets.left;
            int y = insets.top;

            checkBox.setBounds(x, y + (iconSize.height - checkBoxSize.height) / 2, checkBoxSize.width, checkBoxSize.height);
            icon.setBounds(x + checkBoxSize.width + offset.get(), y, iconSize.width, iconSize.height);
          }
        }
      };
    }

    private void createVersion() {
      String version = StringUtil.defaultIfEmpty(myPlugin.getVersion(), null);
      if (version != null) {
        myVersion = new JLabel("Version " + version);
        myVersion.setOpaque(false);
        myBaselinePanel.addVersionComponent(installTiny(myVersion));
      }

      if (myPlugin instanceof PluginNode) {
        String date = getLastUpdatedDate(myPlugin);
        if (date != null) {
          myLastUpdated = new JLabel(date, AllIcons.Plugins.Updated, SwingConstants.CENTER);
          myLastUpdated.setOpaque(false);
          myBaselinePanel.addVersionComponent(installTiny(myLastUpdated));
        }
      }
    }

    public void updateErrors() {
      boolean errors = myPluginModel.hasErrors(myPlugin);
      updateIcon(errors, !myPluginModel.isEnabled(myPlugin));

      if (errors) {
        Ref<Boolean> enableAction = new Ref<>();
        String message = getErrorMessage(myPluginModel, myPlugin, enableAction);
        myBaselinePanel.addErrorComponents(message, !enableAction.isNull(), () -> myPluginModel.enableRequiredPlugins(myPlugin));
      }
      else {
        myBaselinePanel.removeErrorComponents();
      }
    }

    @Nullable
    private static String getChangeNotes(@NotNull IdeaPluginDescriptor plugin) {
      String notes = plugin.getChangeNotes();
      return StringUtil.isEmptyOrSpaces(notes) ? null : "<b>Change Notes</b><br>\n" + notes;
    }

    @Override
    public void setListeners(@NotNull LinkListener<IdeaPluginDescriptor> listener,
                             @NotNull LinkListener<String> searchListener,
                             @NotNull EventHandler eventHandler) {
      super.setListeners(listener, searchListener, eventHandler);
      eventHandler.addAll(this);
      myBaselinePanel.setListeners(eventHandler);
    }

    @Override
    protected void updateColors(@NotNull Color grayedFg, @NotNull Color background) {
      super.updateColors(grayedFg, background);

      if (myVersion != null) {
        myVersion.setForeground(grayedFg);
      }
      if (myLastUpdated != null) {
        myLastUpdated.setForeground(grayedFg);
      }

      boolean enabled = MyPluginModel.isInstallingOrUpdate(myPlugin) || myPluginModel.isEnabled(myPlugin);
      myName.setForeground(enabled ? null : DisabledColor);

      if (myDescription != null) {
        myDescription.setForeground(enabled ? grayedFg : DisabledColor);
      }
    }

    public boolean isEnabledState() {
      return myPluginModel.isEnabled(myPlugin);
    }

    public void updateAfterUninstall() {
      myUninstalled = true;

      myEnableDisableButton.setSelected(false);
      myEnableDisableButton.setEnabled(false);

      changeUpdateToRestart();
    }

    public void changeUpdateToRestart() {
      boolean layout = false;

      if (myUpdateButton != null) {
        myBaselinePanel.removeButtonComponent(myUpdateButton);
        myUpdateButton = null;
        layout = true;
      }
      if (myRestartButton == null) {
        myRestartButton = new RestartButton(myPluginModel);
        myRestartButton.setFocusable(false);
        myBaselinePanel.addButtonComponent(myRestartButton);
        layout = true;
      }

      if (layout) {
        myBaselinePanel.doLayout();
      }
    }

    public void updateEnabledState() {
      if (!myUninstalled) {
        myEnableDisableButton.setSelected(isEnabledState());
      }
      updateErrors();
      setSelection(mySelection, false);
    }

    public void updatePlugin() {
      if (myUpdateButton != null) {
        myUpdateButton.doClick();
      }
    }

    @Override
    public void createPopupMenu(@NotNull DefaultActionGroup group, @NotNull List<CellPluginComponent> selection) {
      for (CellPluginComponent component: selection) {
        if (MyPluginModel.isInstallingOrUpdate(component.myPlugin)) {
          return;
        }
      }

      boolean restart = true;
      for (CellPluginComponent component: selection) {
        if (((ListPluginComponent)component).myRestartButton == null) {
          restart = false;
          break;
        }
      }
      if (restart) {
        group.add(new ButtonAnAction(((ListPluginComponent)selection.get(0)).myRestartButton));
      }
      else {
        int size = selection.size();
        JButton[] buttons = new JButton[size];

        for (int i = 0; i < size; i++) {
          JButton button = ((ListPluginComponent)selection.get(i)).myUpdateButton;
          if (button == null) {
            buttons = null;
            break;
          }
          buttons[i] = button;
        }

        if (buttons != null) {
          group.add(new ButtonAnAction(buttons));
        }
      }

      Pair<Boolean, IdeaPluginDescriptor[]> result = getSelectionNewState(selection);
      group.add(new MyAnAction(result.first ? "Enable" : "Disable", KeyEvent.VK_SPACE) {
        @Override
        public void actionPerformed(AnActionEvent e) {
          myPluginModel.changeEnableDisable(result.second, result.first);
        }
      });

      for (CellPluginComponent component: selection) {
        if (((ListPluginComponent)component).myUninstalled || component.myPlugin.isBundled()) {
          return;
        }
      }

      group.addSeparator();
      group.add(new MyAnAction("Uninstall", KeyEvent.VK_BACK_SPACE) {
        @Override
        public void actionPerformed(AnActionEvent e) {
          for (CellPluginComponent component: selection) {
            myPluginModel.doUninstall(component, component.myPlugin, null);
          }
        }
      });
    }

    @Override
    public void handleKeyAction(int keyCode, @NotNull List<CellPluginComponent> selection) {
      for (CellPluginComponent component: selection) {
        if (MyPluginModel.isInstallingOrUpdate(component.myPlugin)) {
          return;
        }
      }

      if (keyCode == KeyEvent.VK_SPACE) {
        if (selection.size() == 1) {
          myPluginModel.changeEnableDisable(selection.get(0).myPlugin);
        }
        else {
          Pair<Boolean, IdeaPluginDescriptor[]> result = getSelectionNewState(selection);
          myPluginModel.changeEnableDisable(result.second, result.first);
        }
      }
      else if (keyCode == KeyEvent.VK_ENTER) {
        boolean restart = true;
        for (CellPluginComponent component: selection) {
          if (((ListPluginComponent)component).myRestartButton == null) {
            restart = false;
            break;
          }
        }
        if (restart) {
          ((ListPluginComponent)selection.get(0)).myRestartButton.doClick();
          return;
        }

        for (CellPluginComponent component: selection) {
          if (((ListPluginComponent)component).myUpdateButton == null) {
            return;
          }
        }
        for (CellPluginComponent component: selection) {
          ((ListPluginComponent)component).myUpdateButton.doClick();
        }
      }
      else if (keyCode == KeyEvent.VK_BACK_SPACE) {
        for (CellPluginComponent component: selection) {
          if (((ListPluginComponent)component).myUninstalled || component.myPlugin.isBundled()) {
            return;
          }
        }
        for (CellPluginComponent component: selection) {
          myPluginModel.doUninstall(this, component.myPlugin, null);
        }
      }
    }

    @NotNull
    private static Pair<Boolean, IdeaPluginDescriptor[]> getSelectionNewState(@NotNull List<CellPluginComponent> selection) {
      boolean state = ((ListPluginComponent)selection.get(0)).isEnabledState();
      boolean setTrue = false;

      for (Iterator<CellPluginComponent> I = selection.listIterator(1); I.hasNext(); ) {
        if (state != ((ListPluginComponent)I.next()).isEnabledState()) {
          setTrue = true;
          break;
        }
      }

      int size = selection.size();
      IdeaPluginDescriptor[] plugins = new IdeaPluginDescriptor[size];
      for (int i = 0; i < size; i++) {
        plugins[i] = selection.get(i).myPlugin;
      }

      return Pair.create(setTrue || !state, plugins);
    }

    @Override
    public void close() {
      if (myIndicator != null) {
        myPluginModel.removeProgress(myPlugin, myIndicator);
        myIndicator = null;
      }
      myPluginModel.removeComponent(this);
    }
  }

  private static class ButtonAnAction extends AnAction {
    private final JButton[] myButtons;

    public ButtonAnAction(@NotNull JButton... buttons) {
      super(buttons[0].getText());
      myButtons = buttons;
      setShortcutSet(CommonShortcuts.ENTER);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      for (JButton button: myButtons) {
        button.doClick();
      }
    }
  }

  private abstract static class MyAnAction extends AnAction {
    public MyAnAction(@Nullable String text, int keyCode) {
      super(text);
      setShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(keyCode, 0)));
    }
  }

  private static class BaselinePanel extends NonOpaquePanel {
    private Component myBaseComponent;
    private final List<Component> myVersionComponents = new ArrayList<>();
    private final List<Component> myButtonComponents = new ArrayList<>();
    private Component myProgressComponent;

    private final JBValue myOffset = new JBValue.Float(8);
    private final JBValue myBeforeButtonOffset = new JBValue.Float(40);
    private final JBValue myButtonOffset = new JBValue.Float(6);

    private JLabel myErrorComponent;
    private Component myErrorEnableComponent;

    private EventHandler myEventHandler;

    public BaselinePanel() {
      setBorder(JBUI.Borders.empty(5, 0, 6, 0));

      setLayout(new AbstractLayoutManager() {
        @Override
        public Dimension preferredLayoutSize(Container parent) {
          Dimension baseSize = myBaseComponent.getPreferredSize();
          int width = baseSize.width;

          if (myProgressComponent == null) {
            for (Component component: myVersionComponents) {
              if (!component.isVisible()) {
                break;
              }
              width += myOffset.get() + component.getPreferredSize().width;
            }

            if (myErrorComponent != null) {
              width += myOffset.get() + myErrorComponent.getPreferredSize().width;

              if (myErrorEnableComponent != null) {
                width += myOffset.get() + myErrorEnableComponent.getPreferredSize().width;
              }
            }

            int size = myButtonComponents.size();
            if (size > 0) {
              width += myBeforeButtonOffset.get();
              width += (size - 1) * myButtonOffset.get();

              for (Component component: myButtonComponents) {
                width += component.getPreferredSize().width;
              }
            }
          }
          else {
            width += myOffset.get() + myProgressComponent.getPreferredSize().width;
          }

          Insets insets = parent.getInsets();
          return new Dimension(width, insets.top + baseSize.height + insets.bottom);
        }

        @Override
        public void layoutContainer(Container parent) {
          Dimension baseSize = myBaseComponent.getPreferredSize();
          int top = parent.getInsets().top;
          int y = top + myBaseComponent.getBaseline(baseSize.width, baseSize.height);
          int x = 0;

          myBaseComponent.setBounds(x, top, baseSize.width, baseSize.height);
          x += baseSize.width;

          if (myProgressComponent != null) {
            Dimension size = myProgressComponent.getPreferredSize();
            setBaselineBounds(parent.getWidth() - size.width, y, myProgressComponent, size);
            return;
          }

          for (Component component: myVersionComponents) {
            if (!component.isVisible()) {
              break;
            }
            Dimension size = component.getPreferredSize();
            x += myOffset.get();
            setBaselineBounds(x, y, component, size);
            x += size.width;
          }

          int lastX = parent.getWidth();

          for (int i = myButtonComponents.size() - 1; i >= 0; i--) {
            Component component = myButtonComponents.get(i);
            Dimension size = component.getPreferredSize();
            lastX -= size.width;
            setBaselineBounds(lastX, y, component, size);
            lastX -= myButtonOffset.get();
          }

          if (myErrorComponent != null) {
            x += myOffset.get();

            if (myErrorEnableComponent != null) {
              if (!myButtonComponents.isEmpty()) {
                lastX -= myBeforeButtonOffset.get();
              }

              lastX -= myErrorEnableComponent.getPreferredSize().width;
              lastX -= myOffset.get();
            }

            int errorWidth = lastX - x;
            Dimension size = myErrorComponent.getPreferredSize();

            if (errorWidth >= size.width) {
              setBaselineBounds(x, y, myErrorComponent, size);
              myErrorComponent.setToolTipText(null);
              x += size.width;
            }
            else {
              setBaselineBounds(x, y, myErrorComponent, size, errorWidth, size.height);
              myErrorComponent.setToolTipText(myErrorComponent.getText());
              x += errorWidth;
            }

            if (myErrorEnableComponent != null) {
              x += myOffset.get();
              setBaselineBounds(x, y, myErrorEnableComponent, myErrorEnableComponent.getPreferredSize());
            }
          }
        }

        private void setBaselineBounds(int x, int y, @NotNull Component component, @NotNull Dimension size) {
          setBaselineBounds(x, y, component, size, size.width, size.height);
        }

        private void setBaselineBounds(int x, int y, @NotNull Component component, @NotNull Dimension prefSize, int width, int height) {
          component.setBounds(x, y - component.getBaseline(prefSize.width, prefSize.height), width, height);
        }
      });
    }

    public void setListeners(@NotNull EventHandler eventHandler) {
      myEventHandler = eventHandler;
    }

    @Override
    public Component add(Component component) {
      assert myBaseComponent == null;
      myBaseComponent = component;
      return super.add(component);
    }

    public void addVersionComponent(@NotNull JComponent component) {
      myVersionComponents.add(component);
      add(component, null);
    }

    public void addErrorComponents(@NotNull String message, boolean enableAction, @NotNull Runnable enableCallback) {
      if (myErrorComponent == null) {
        myErrorComponent = new JLabel();
        myErrorComponent.setForeground(JBColor.red);
        myErrorComponent.setOpaque(false);
        add(myErrorComponent, null);

        if (myEventHandler != null) {
          myEventHandler.add(myErrorComponent);
        }
      }
      myErrorComponent.setText(message);

      if (enableAction) {
        if (myErrorEnableComponent == null) {
          LinkLabel<Object> errorAction = new LinkLabel<>("Enable", null);
          errorAction.setOpaque(false);
          errorAction.setListener((aSource, aLinkData) -> enableCallback.run(), null);
          add(myErrorEnableComponent = errorAction, null);

          if (myEventHandler != null) {
            myEventHandler.add(errorAction);
          }
        }
      }
      else if (myErrorEnableComponent != null) {
        remove(myErrorEnableComponent);
        myErrorEnableComponent = null;
      }

      for (Component component: myVersionComponents) {
        component.setVisible(false);
      }
      doLayout();
    }

    public void removeErrorComponents() {
      if (myErrorComponent != null) {
        remove(myErrorComponent);
        myErrorComponent = null;

        if (myErrorEnableComponent != null) {
          remove(myErrorEnableComponent);
          myErrorEnableComponent = null;
        }

        for (Component component: myVersionComponents) {
          component.setVisible(true);
        }
        doLayout();
      }
    }

    public void addButtonComponent(@NotNull JComponent component) {
      myButtonComponents.add(component);
      add(component, null);
    }

    public void removeButtonComponent(@NotNull JComponent component) {
      myButtonComponents.remove(component);
      remove(component);
    }

    public void setProgressComponent(@NotNull CellPluginComponent pluginComponent, @NotNull JComponent progressComponent) {
      assert myProgressComponent == null;
      myProgressComponent = progressComponent;
      add(progressComponent, null);

      if (myEventHandler != null) {
        myEventHandler.addAll(progressComponent);
        myEventHandler.updateHover(pluginComponent);
      }

      setVisibleOther(false);
      doLayout();
    }

    public void removeProgressComponent() {
      assert myProgressComponent != null;
      remove(myProgressComponent);
      myProgressComponent = null;

      setVisibleOther(true);
      doLayout();
    }

    private void setVisibleOther(boolean value) {
      for (Component component: myVersionComponents) {
        component.setVisible(value);
      }
      if (myErrorComponent != null) {
        myErrorComponent.setVisible(value);
      }
      if (myErrorEnableComponent != null) {
        myErrorEnableComponent.setVisible(value);
      }
      for (Component component: myButtonComponents) {
        component.setVisible(value);
      }
    }
  }

  private static class GridCellPluginComponent extends CellPluginComponent {
    private final MyPluginModel myPluginModel;
    private JLabel myLastUpdated;
    private JLabel myDownloads;
    private JLabel myRating;
    private final JButton myInstallButton = new InstallButton(false);
    private JComponent myLastComponent;
    private List<TagComponent> myTagComponents;
    private ProgressIndicatorEx myIndicator;

    public GridCellPluginComponent(@NotNull MyPluginModel pluginsModel,
                                   @NotNull IdeaPluginDescriptor plugin,
                                   @NotNull TagBuilder tagBuilder) {
      super(plugin);
      myPluginModel = pluginsModel;
      pluginsModel.addComponent(this);

      JPanel container = new NonOpaquePanel(new BorderLayout(JBUI.scale(10), 0));
      add(container);
      addIconComponent(container, BorderLayout.WEST);

      JPanel centerPanel = new NonOpaquePanel(new VerticalLayout(offset5(), JBUI.scale(180)));
      container.add(centerPanel);

      addNameComponent(centerPanel);
      addTags(centerPanel, tagBuilder);
      addDescriptionComponent(centerPanel, getShortDescription(myPlugin, false), new LineFunction(3, true));

      createMetricsPanel(centerPanel);

      addInstallButton();

      setOpaque(true);
      setBorder(JBUI.Borders.empty(10));

      setLayout(new AbstractLayoutManager() {
        @Override
        public Dimension preferredLayoutSize(Container parent) {
          Dimension size = container.getPreferredSize();
          size.height += offset5();
          size.height += myLastComponent.getPreferredSize().height;
          JBInsets.addTo(size, parent.getInsets());
          return size;
        }

        @Override
        public void layoutContainer(Container parent) {
          Insets insets = parent.getInsets();
          Dimension size = container.getPreferredSize();
          Rectangle bounds = new Rectangle(insets.left, insets.top, size.width, size.height);
          container.setBounds(bounds);
          container.doLayout();

          Point location = centerPanel.getLocation();
          Dimension buttonSize = myLastComponent.getPreferredSize();
          Border border = myLastComponent.getBorder();
          int borderOffset = border == null ? 0 : border.getBorderInsets(myLastComponent).left;
          myLastComponent
            .setBounds(bounds.x + location.x - borderOffset, bounds.y + offset5() + bounds.height, Math.min(buttonSize.width, size.width),
                       buttonSize.height);
        }
      });

      updateIcon(false, false);
      setSelection(SelectionType.NONE);
    }

    private void createMetricsPanel(@NotNull JPanel centerPanel) {
      if (!(myPlugin instanceof PluginNode)) {
        return;
      }

      String downloads = getDownloads(myPlugin);
      String date = getLastUpdatedDate(myPlugin);
      String rating = getRating(myPlugin);

      if (downloads != null || date != null || rating != null) {
        JPanel panel = new NonOpaquePanel(new HorizontalLayout(JBUI.scale(7)));
        centerPanel.add(panel);

        if (date != null) {
          myLastUpdated = new JLabel(date, AllIcons.Plugins.Updated, SwingConstants.CENTER);
          myLastUpdated.setOpaque(false);
          panel.add(installTiny(myLastUpdated));
        }

        if (downloads != null) {
          myDownloads = new JLabel(downloads, AllIcons.Plugins.Downloads, SwingConstants.CENTER);
          myDownloads.setOpaque(false);
          panel.add(installTiny(myDownloads));
        }

        if (rating != null) {
          myRating = new JLabel(rating, AllIcons.Plugins.Rating, SwingConstants.CENTER);
          myRating.setOpaque(false);
          panel.add(installTiny(myRating));
        }
      }
    }

    private void addInstallButton() {
      if (InstalledPluginsState.getInstance().wasInstalled(myPlugin.getPluginId())) {
        RestartButton restartButton = new RestartButton(myPluginModel);
        restartButton.setFocusable(false);
        add(myLastComponent = restartButton);
        return;
      }

      myInstallButton.setFocusable(false);
      myInstallButton.addActionListener(e -> myPluginModel.installOrUpdatePlugin(myPlugin, true));
      myInstallButton.setEnabled(PluginManager.getPlugin(myPlugin.getPluginId()) == null);
      add(myLastComponent = myInstallButton);

      if (MyPluginModel.isInstallingOrUpdate(myPlugin)) {
        showProgress(false);
      }
    }

    public void showProgress() {
      showProgress(true);
    }

    private void showProgress(boolean repaint) {
      MyTwoLineProgressIndicator indicator = new MyTwoLineProgressIndicator();
      indicator.setCancelRunnable(() -> myPluginModel.finishInstall(myPlugin, false));
      myIndicator = indicator;

      myInstallButton.setVisible(false);
      add(myLastComponent = indicator.getComponent());
      doLayout();

      myPluginModel.addProgress(myPlugin, indicator);

      if (repaint) {
        fullRepaint();
      }
    }

    public void hideProgress(boolean success) {
      myIndicator = null;
      JComponent lastComponent = myLastComponent;
      if (success) {
        RestartButton restartButton = new RestartButton(myPluginModel);
        restartButton.setFocusable(false);
        add(myLastComponent = restartButton);
      }
      else {
        myLastComponent = myInstallButton;
        myInstallButton.setVisible(true);
      }
      remove(lastComponent);
      doLayout();
      fullRepaint();
    }

    private void addTags(@NotNull JPanel parent, @NotNull TagBuilder tagBuilder) {
      List<String> tags = getTags(myPlugin);
      if (tags.isEmpty()) {
        return;
      }

      NonOpaquePanel panel = new NonOpaquePanel(new HorizontalLayout(JBUI.scale(6)));
      parent.add(panel);

      myTagComponents = new ArrayList<>();

      for (String tag: tags) {
        TagComponent component = tagBuilder.createTagComponent(tag);
        panel.add(component);
        myTagComponents.add(component);
      }
    }

    @Override
    public void setListeners(@NotNull LinkListener<IdeaPluginDescriptor> listener,
                             @NotNull LinkListener<String> searchListener,
                             @NotNull EventHandler eventHandler) {
      super.setListeners(listener, searchListener, eventHandler);

      if (myDescription != null) {
        UIUtil.setCursor(myDescription, Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        myDescription.addMouseListener(new MouseAdapter() {
          @Override
          public void mouseClicked(MouseEvent event) {
            if (SwingUtilities.isLeftMouseButton(event)) {
              listener.linkSelected(myName, myPlugin);
            }
          }
        });
        myDescription.addMouseListener(myHoverNameListener);
      }

      if (myTagComponents != null) {
        for (TagComponent component: myTagComponents) {
          //noinspection unchecked
          component.setListener(searchListener, "tag:" + component.getText());
        }
        myTagComponents = null;
      }
    }

    @Override
    protected void updateColors(@NotNull Color grayedFg, @NotNull Color background) {
      super.updateColors(grayedFg, background);

      if (myLastUpdated != null) {
        myLastUpdated.setForeground(grayedFg);
      }
      if (myDownloads != null) {
        myDownloads.setForeground(grayedFg);
      }
      if (myRating != null) {
        myRating.setForeground(grayedFg);
      }
    }

    @Override
    public void close() {
      if (myIndicator != null) {
        myPluginModel.removeProgress(myPlugin, myIndicator);
        myIndicator = null;
      }
      myPluginModel.removeComponent(this);
    }
  }

  private static int offset5() {
    return JBUI.scale(5);
  }

  private enum SelectionType {
    SELECTION, HOVER, NONE
  }

  private class DetailsPagePluginComponent extends OpaquePanel {
    private final IdeaPluginDescriptor myPlugin;
    private JLabel myNameComponent;
    private JLabel myIconLabel;
    private JButton myUpdateButton;
    private JButton myInstallButton;
    private JButton myEnableDisableButton;
    private RestartButton myRestartButton;
    private JBOptionButton myEnableDisableUninstallButton;
    private JPanel myButtonsPanel;
    private final JPanel myCenterPanel;
    private MyProgressIndicator myIndicator;

    public int backTabIndex;

    public DetailsPagePluginComponent(@NotNull IdeaPluginDescriptor plugin, boolean update) {
      super(new BorderLayout(0, JBUI.scale(32)), MAIN_BG_COLOR);
      myPlugin = plugin;

      setBorder(JBUI.Borders.empty(15, 20, 0, 0));

      JPanel header = createHeaderPanel();
      myCenterPanel = createCenterPanel(update);
      header.add(myCenterPanel);

      createTagPanel();
      createMetricsPanel();
      createErrorPanel();
      createProgressPanel(!update);
      createBottomPanel();
    }

    @NotNull
    private JPanel createCenterPanel(boolean update) {
      JPanel centerPanel = new NonOpaquePanel(new VerticalLayout(offset5()));
      JPanel nameButtons = new NonOpaquePanel(new BorderLayout(offset5(), 0));

      myNameComponent = new JLabel(myPlugin.getName());
      myNameComponent.setOpaque(false);
      Font font = myNameComponent.getFont();
      if (font != null) {
        myNameComponent.setFont(font.deriveFont(Font.BOLD, 30));
      }
      if (!(myPlugin instanceof PluginNode) && !myPluginsModel.isEnabled(myPlugin)) {
        myNameComponent.setForeground(DisabledColor);
      }

      nameButtons.add(myNameComponent, BorderLayout.WEST);
      nameButtons.add(myButtonsPanel = createButtons(update), BorderLayout.EAST);
      centerPanel.add(nameButtons, VerticalLayout.FILL_HORIZONTAL);

      boolean bundled = myPlugin.isBundled();
      String version = bundled ? "bundled" : myPlugin.getVersion();

      if (!StringUtil.isEmptyOrSpaces(version)) {
        if (!bundled) {
          version = "v" + version;
        }
        JLabel versionComponent = new JLabel(version);
        versionComponent.setOpaque(false);
        versionComponent.setForeground(new JBColor(Gray._130, Gray._120));
        nameButtons.add(versionComponent);

        int nameBaseline = myNameComponent.getBaseline(myNameComponent.getWidth(), myNameComponent.getHeight());
        int versionBaseline = versionComponent.getBaseline(versionComponent.getWidth(), versionComponent.getHeight());
        versionComponent.setBorder(JBUI.Borders.empty(nameBaseline - versionBaseline + 6, 4, 0, 0));
      }

      return centerPanel;
    }

    @NotNull
    private JPanel createButtons(boolean update) {
      JPanel buttons = new NonOpaquePanel(new HorizontalLayout(JBUI.scale(6)));
      buttons.setBorder(JBUI.Borders.emptyTop(1));

      InstalledPluginsState pluginsState = InstalledPluginsState.getInstance();
      PluginId id = myPlugin.getPluginId();

      if ((myPlugin instanceof IdeaPluginDescriptorImpl && ((IdeaPluginDescriptorImpl)myPlugin).isDeleted()) ||
          pluginsState.wasInstalled(id) ||
          pluginsState.wasUpdated(id)) {
        buttons.add(myRestartButton = new RestartButton(myPluginsModel));
      }
      else {
        boolean stateActions = true;
        if (update) {
          buttons.add(myUpdateButton = new UpdateButton());
        }
        else if (myPlugin instanceof PluginNode) {
          buttons.add(myInstallButton = new InstallButton(true));
          myInstallButton.setEnabled(PluginManager.getPlugin(myPlugin.getPluginId()) == null);
          stateActions = false;
        }
        if (stateActions) {
          if (myPlugin.isBundled()) {
            myEnableDisableButton = new JButton(myPluginsModel.getEnabledTitle(myPlugin));
            myEnableDisableButton.addActionListener(e -> changeEnableDisable());
            setWidth72(myEnableDisableButton);
            buttons.add(myEnableDisableButton);
          }
          else {
            AbstractAction enableDisableAction = new AbstractAction(myPluginsModel.getEnabledTitle(myPlugin)) {
              @Override
              public void actionPerformed(ActionEvent e) {
                changeEnableDisable();
              }
            };
            AbstractAction uninstallAction = new AbstractAction("Uninstall") {
              @Override
              public void actionPerformed(ActionEvent e) {
                doUninstall();
              }
            };
            buttons.add(myEnableDisableUninstallButton = new MyOptionButton(enableDisableAction, uninstallAction));
          }
        }
      }

      for (Component component: uiChildren(buttons)) {
        component.setBackground(MAIN_BG_COLOR);
      }

      return buttons;
    }

    @NotNull
    private JPanel createHeaderPanel() {
      JPanel header = new NonOpaquePanel(new BorderLayout(JBUI.scale(20), 0));
      header.setBorder(JBUI.Borders.emptyRight(20));
      add(header, BorderLayout.NORTH);

      myIconLabel = new JLabel();
      updateIcon();
      myIconLabel.setVerticalAlignment(SwingConstants.TOP);
      myIconLabel.setOpaque(false);
      myIconLabel.setEnabled(myPlugin instanceof PluginNode || myPluginsModel.isEnabled(myPlugin));
      header.add(myIconLabel, BorderLayout.WEST);

      return header;
    }

    private void updateIcon() {
      boolean jb = isJBPlugin(myPlugin);
      boolean errors = myPluginsModel.hasErrors(myPlugin);

      myIconLabel.setIcon(PluginLogoInfo.getIcon(true, jb, errors, false));
      myIconLabel.setDisabledIcon(PluginLogoInfo.getIcon(true, jb, errors, true));
    }

    private void createTagPanel() {
      java.util.List<String> tags = getTags(myPlugin);

      if (!tags.isEmpty()) {
        NonOpaquePanel tagPanel = new NonOpaquePanel(new HorizontalLayout(JBUI.scale(6)));
        tagPanel.setBorder(JBUI.Borders.emptyTop(2));
        myCenterPanel.add(tagPanel);

        for (String tag: tags) {
          TagComponent component = myTagBuilder.createTagComponent(tag);
          //noinspection unchecked
          component.setListener(mySearchListener, "tag:" + tag);
          tagPanel.add(component);
        }
      }
    }

    private void createMetricsPanel() {
      if (!(myPlugin instanceof PluginNode)) {
        return;
      }

      Color grayedFg = new JBColor(Gray._130, Gray._120);

      String downloads = getDownloads(myPlugin);
      String date = getLastUpdatedDate(myPlugin);
      String rating = getRating(myPlugin);

      if (downloads != null || date != null || rating != null) {
        JPanel metrics = new NonOpaquePanel(new HorizontalLayout(JBUI.scale(20)));
        metrics.setBorder(JBUI.Borders.emptyTop(3));
        myCenterPanel.add(metrics);

        if (date != null) {
          JLabel lastUpdated = new JLabel(date, AllIcons.Plugins.Updated, SwingConstants.CENTER);
          lastUpdated.setOpaque(false);
          lastUpdated.setForeground(grayedFg);
          metrics.add(installTiny(lastUpdated));
        }

        if (downloads != null) {
          JLabel downloadsComponent = new JLabel(downloads, AllIcons.Plugins.Downloads, SwingConstants.CENTER);
          downloadsComponent.setOpaque(false);
          downloadsComponent.setForeground(grayedFg);
          metrics.add(installTiny(downloadsComponent));
        }

        if (rating != null) {
          RatesPanel ratesPanel = new RatesPanel();
          ratesPanel.setRate(rating);
          metrics.add(installTiny(ratesPanel));
        }
      }
    }

    private void createErrorPanel() {
      if (myPluginsModel.hasErrors(myPlugin)) {
        JPanel errorPanel = new NonOpaquePanel(new HorizontalLayout(JBUI.scale(8)));
        errorPanel.setBorder(JBUI.Borders.emptyTop(15));
        myCenterPanel.add(errorPanel);

        JLabel errorMessage = new JLabel();
        errorMessage.setForeground(JBColor.red);
        errorMessage.setOpaque(false);
        errorPanel.add(errorMessage);

        Ref<Boolean> enableAction = new Ref<>();
        errorMessage.setText(getErrorMessage(myPluginsModel, myPlugin, enableAction));

        if (!enableAction.isNull()) {
          LinkLabel<Object> errorAction = new LinkLabel<>("Enable", null);
          errorAction.setOpaque(false);
          errorPanel.add(errorAction);

          errorAction.setListener((aSource, aLinkData) -> {
            myPluginsModel.enableRequiredPlugins(myPlugin);

            myCenterPanel.remove(errorPanel);
            createErrorPanel();
            myCenterPanel.doLayout();

            updateIcon();
            updateEnabledState();

            doLayout();
            revalidate();
            repaint();
          }, null);
        }
      }
    }

    private void createProgressPanel(boolean install) {
      JButton button = myInstallButton == null ? myUpdateButton : myInstallButton;
      if (button == null) {
        return;
      }

      button.addActionListener(e -> myPluginsModel.installOrUpdatePlugin(myPlugin, install));

      if (MyPluginModel.isInstallingOrUpdate(myPlugin)) {
        showProgress(false);
      }
    }

    public void showProgress() {
      showProgress(true);
    }

    private void showProgress(boolean repaint) {
      myIndicator = new MyProgressIndicator();
      myIndicator.setCancelRunnable(() -> myPluginsModel.finishInstall(myPlugin, false));

      myButtonsPanel.remove(myInstallButton == null ? myUpdateButton : myInstallButton);
      if (myEnableDisableButton != null) {
        myButtonsPanel.remove(myEnableDisableButton);
      }
      if (myEnableDisableUninstallButton != null) {
        myButtonsPanel.remove(myEnableDisableUninstallButton);
      }
      myButtonsPanel.doLayout();
      myCenterPanel.add(myIndicator.getComponent());

      myPluginsModel.addProgress(myPlugin, myIndicator);

      if (repaint) {
        doLayout();
        revalidate();
        repaint();
      }
    }

    public void hideProgress(boolean success) {
      myCenterPanel.remove(myIndicator.getComponent());
      myIndicator = null;

      if (success) {
        changeInstallOrUpdateToRestart();
      }
      else {
        myButtonsPanel.add(myInstallButton == null ? myUpdateButton : myInstallButton);
        if (myEnableDisableButton != null) {
          myButtonsPanel.add(myEnableDisableButton);
        }
        if (myEnableDisableUninstallButton != null) {
          myButtonsPanel.add(myEnableDisableUninstallButton);
        }

        doLayout();
        revalidate();
        repaint();
      }
    }

    public void close() {
      if (myIndicator != null) {
        myPluginsModel.removeProgress(myPlugin, myIndicator);
        myIndicator = null;
      }
    }

    private void createBottomPanel() {
      String description = getDescriptionAndChangeNotes();
      String vendor = myPlugin.isBundled() ? null : myPlugin.getVendor();
      String size = myPlugin instanceof PluginNode ? ((PluginNode)myPlugin).getSize() : null;

      if (!StringUtil.isEmptyOrSpaces(description) || !StringUtil.isEmptyOrSpaces(vendor) || !StringUtil.isEmptyOrSpaces(size)) {
        JPanel bottomPanel = new OpaquePanel(new VerticalLayout(offset5()), MAIN_BG_COLOR);
        bottomPanel.setBorder(JBUI.Borders.emptyBottom(15));

        JBScrollPane scrollPane = new JBScrollPane(bottomPanel);
        scrollPane.getVerticalScrollBar().setBackground(MAIN_BG_COLOR);
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setBorder(null);
        add(scrollPane);

        if (!StringUtil.isEmptyOrSpaces(description)) {
          JEditorPane descriptionComponent = new JEditorPane();
          descriptionComponent.setEditorKit(UIUtil.getHTMLEditorKit());
          descriptionComponent.setEditable(false);
          descriptionComponent.setFocusable(false);
          descriptionComponent.setOpaque(false);
          descriptionComponent.setBorder(null);
          descriptionComponent.setText(XmlStringUtil.wrapInHtml(description));

          if (descriptionComponent.getCaret() != null) {
            descriptionComponent.setCaretPosition(0);
          }

          bottomPanel.add(descriptionComponent, JBUI.scale(700), -1);
        }

        if (!StringUtil.isEmptyOrSpaces(vendor) || !StringUtil.isEmptyOrSpaces(size)) {
          java.util.List<JLabel> labels = new ArrayList<>();

          if (!StringUtil.isEmptyOrSpaces(vendor)) {
            JPanel linePanel = createLabelsPanel(bottomPanel, labels, "Vendor:", vendor, myPlugin.getVendorUrl());
            linePanel.setBorder(JBUI.Borders.emptyTop(20));
          }

          if (!StringUtil.isEmptyOrSpaces(size)) {
            createLabelsPanel(bottomPanel, labels, "Size:", PluginManagerColumnInfo.getFormattedSize(size), null);
          }

          if (labels.size() > 1) {
            int width = 0;
            for (JLabel label: labels) {
              width = Math.max(width, label.getPreferredSize().width);
            }
            for (JLabel label: labels) {
              label.setPreferredSize(new Dimension(width, label.getPreferredSize().height));
            }
          }
        }
      }
    }

    @Nullable
    private String getDescriptionAndChangeNotes() {
      StringBuilder result = new StringBuilder();

      String description = myPlugin.getDescription();
      if (!StringUtil.isEmptyOrSpaces(description)) {
        result.append(description);
      }

      String notes = myPlugin.getChangeNotes();
      if (!StringUtil.isEmptyOrSpaces(notes)) {
        result.append("<h4>Change Notes</h4>").append(notes);
      }

      return result.length() > 0 ? result.toString() : null;
    }

    private void changeEnableDisable() {
      myPluginsModel.changeEnableDisable(myPlugin);
      updateEnabledState();
    }

    private void updateEnabledState() {
      if (!(myPlugin instanceof PluginNode)) {
        boolean enabled = myPluginsModel.isEnabled(myPlugin);
        myNameComponent.setForeground(enabled ? null : DisabledColor);
        myIconLabel.setEnabled(enabled);
      }

      String title = myPluginsModel.getEnabledTitle(myPlugin);
      if (myEnableDisableButton != null) {
        myEnableDisableButton.setText(title);
      }
      if (myEnableDisableUninstallButton != null) {
        myEnableDisableUninstallButton.setText(title);
      }
    }

    private void doUninstall() {
      myPluginsModel.doUninstall(this, myPlugin, this::changeInstallOrUpdateToRestart);
    }

    private void changeInstallOrUpdateToRestart() {
      if (myEnableDisableUninstallButton != null) {
        myButtonsPanel.remove(myEnableDisableUninstallButton);
        myEnableDisableUninstallButton = null;
      }

      if (myUpdateButton != null) {
        myButtonsPanel.remove(myUpdateButton);
        myUpdateButton = null;
      }
      if (myInstallButton != null) {
        myButtonsPanel.remove(myInstallButton);
        myInstallButton = null;
      }
      if (myRestartButton == null) {
        myButtonsPanel.add(myRestartButton = new RestartButton(myPluginsModel));
      }

      doLayout();
      revalidate();
      repaint();
    }
  }

  private static class LinkComponent extends LinkLabel {
    public LinkComponent() {
      super("", null);
    }

    @Override
    protected Color getTextColor() {
      return getForeground();
    }
  }

  private static class TagComponent extends LinkComponent {
    private final Color myColor;

    public TagComponent(@NotNull String name, @Nullable String tooltip, @NotNull Color color) {
      myColor = color;
      setText(name);
      if (tooltip != null) {
        setToolTipText(tooltip);
      }
      setForeground(new JBColor(0x787878, 0x999999));
      setPaintUnderline(false);
      setOpaque(false);
      setBorder(JBUI.Borders.empty(1, 8));
    }

    @Override
    protected void paintComponent(Graphics g) {
      //noinspection UseJBColor
      g.setColor(myUnderline ? new Color(myColor.getRed(), myColor.getGreen(), myColor.getBlue(), 178) : myColor);
      g.fillRect(0, 0, getWidth(), getHeight());
      super.paintComponent(g);
    }

    @Override
    protected boolean isInClickableArea(Point pt) {
      return true;
    }
  }

  private interface TagBuilder {
    @NotNull
    TagComponent createTagComponent(@NotNull String tag);
  }

  private static class MyOptionButton extends JBOptionButton {
    public MyOptionButton(Action action, Action option) {
      super(action, new Action[]{option});
      setWidth72(this);
    }

    @Override
    public void setBackground(Color bg) {
      super.setBackground(bg);
      int count = getComponentCount();
      for (int i = 0; i < count; i++) {
        getComponent(i).setBackground(bg);
      }
    }

    @Override
    public int getBaseline(int width, int height) {
      if (getComponentCount() == 2) {
        Component component = getComponent(0);
        Dimension size = component.getPreferredSize();
        return component.getBaseline(size.width, size.height);
      }
      return super.getBaseline(width, height);
    }
  }

  private static class HorizontalLayout extends AbstractLayoutManager {
    protected final int myOffset;

    public HorizontalLayout(int offset) {
      myOffset = offset;
    }

    @Override
    public Dimension preferredLayoutSize(Container parent) {
      int width = 0;
      int height = 0;
      int count = parent.getComponentCount();

      for (int i = 0; i < count; i++) {
        Dimension size = parent.getComponent(i).getPreferredSize();
        width += size.width;
        height = Math.max(height, size.height);
      }

      width += (count - 1) * myOffset;

      Dimension size = new Dimension(width, height);
      JBInsets.addTo(size, parent.getInsets());
      return size;
    }

    @Override
    public void layoutContainer(Container parent) {
      Insets insets = parent.getInsets();
      int height = parent.getHeight() - insets.top - insets.bottom;
      int x = insets.left;
      int count = parent.getComponentCount();

      for (int i = 0; i < count; i++) {
        Component component = parent.getComponent(i);
        Dimension size = component.getPreferredSize();
        component.setBounds(x, insets.top + (height - size.height) / 2, size.width, size.height);
        x += size.width + myOffset;
      }
    }
  }

  private static class VerticalLayout extends AbstractLayoutManager {
    public static final String FILL_HORIZONTAL = "fill_h";

    private final int myOffset;
    private final int myWidth;
    private final Set<Component> myFillComponents = new HashSet<>();
    private final Map<Component, Integer> myMaxComponents = new HashMap<>();

    public VerticalLayout(int offset) {
      this(offset, 0);
    }

    public VerticalLayout(int offset, int width) {
      myOffset = offset;
      myWidth = width;
    }

    @Override
    public void addLayoutComponent(Component component, Object constraints) {
      if (FILL_HORIZONTAL.equals(constraints)) {
        myFillComponents.add(component);
      }
      else if (constraints instanceof Integer) {
        myMaxComponents.put(component, (Integer)constraints);
      }
    }

    @Override
    public void removeLayoutComponent(Component component) {
      myFillComponents.remove(component);
    }

    @Override
    public Dimension preferredLayoutSize(Container parent) {
      int width = 0;
      int height = 0;
      int count = parent.getComponentCount();

      for (int i = 0; i < count; i++) {
        Dimension size = parent.getComponent(i).getPreferredSize();
        width = Math.max(width, size.width);
        height += size.height;
      }

      height += (count - 1) * myOffset;

      Dimension size = new Dimension(myWidth > 0 ? myWidth : width, height);
      JBInsets.addTo(size, parent.getInsets());
      return size;
    }

    @Override
    public void layoutContainer(Container parent) {
      Insets insets = parent.getInsets();
      int width = parent.getWidth() - insets.left - insets.right;
      int y = insets.top;
      int count = parent.getComponentCount();

      for (int i = 0; i < count; i++) {
        Component component = parent.getComponent(i);
        Dimension size = component.getPreferredSize();
        int componentWidth;
        if (myFillComponents.contains(component)) {
          componentWidth = width;
        }
        else {
          componentWidth = Math.min(width, size.width);
          Integer maxWidth = myMaxComponents.get(component);
          if (maxWidth != null) {
            componentWidth = Math.min(componentWidth, maxWidth);
          }
        }
        component.setBounds(insets.left, y, componentWidth, size.height);
        y += size.height + myOffset;
      }
    }
  }

  private static final Color DisabledColor = new JBColor(0xC6C6C6, 0x575859);

  @SuppressWarnings("UseJBColor")
  private static final Color WhiteForeground = new JBColor(Color.white, new Color(0xBBBBBB));
  @SuppressWarnings("UseJBColor")
  private static final Color WhiteBackground = new JBColor(Color.white, new Color(0x3C3F41));
  private static final Color BlueColor = new JBColor(0x1D73BF, 0x134D80);
  private static final Color GreenColor = new JBColor(0x5D9B47, 0x457335);
  @SuppressWarnings("UseJBColor")
  private static final Color GreenFocusedBackground = new Color(0xE1F6DA);

  private static class UpdateButton extends ColorButton {
    public UpdateButton() {
      setTextColor(WhiteForeground);
      setBgColor(BlueColor);
      setBorderColor(BlueColor);

      setText("Update");
      setWidth72(this);
    }
  }

  private static class RestartButton extends InstallButton {
    public RestartButton(@NotNull MyPluginModel pluginModel) {
      super(true);
      addActionListener(e -> IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(() -> {
        pluginModel.needRestart = true;
        pluginModel.createShutdownCallback = false;

        DialogWrapper settings = DialogWrapper.findInstance(IdeFocusManager.findInstance().getFocusOwner());
        assert settings instanceof SettingsDialog : settings;
        ((SettingsDialog)settings).doOKAction();

        ((ApplicationImpl)ApplicationManager.getApplication()).exit(true, false, true);
      }, ModalityState.current()));
    }

    @Override
    protected void setTextAndSize() {
      setText("Restart IDE");
    }
  }

  private static class InstallButton extends ColorButton {
    public InstallButton(boolean fill) {
      if (fill) {
        setTextColor(WhiteForeground);
        setBgColor(GreenColor);
      }
      else {
        setTextColor(GreenColor);
        setFocusedTextColor(GreenColor);
        setBgColor(WhiteBackground);
      }

      setFocusedBgColor(GreenFocusedBackground);
      setBorderColor(GreenColor);
      setFocusedBorderColor(GreenColor);

      setTextAndSize();
    }

    protected void setTextAndSize() {
      setText("Install");
      setWidth72(this);
    }
  }

  private static class ColorButton extends JButton {
    public ColorButton() {
      setOpaque(false);
    }

    protected final void setTextColor(@NotNull Color color) {
      putClientProperty("JButton.textColor", color);
    }

    protected final void setFocusedTextColor(@NotNull Color color) {
      putClientProperty("JButton.focusedTextColor", color);
    }

    protected final void setBgColor(@NotNull Color color) {
      putClientProperty("JButton.backgroundColor", color);
    }

    protected final void setFocusedBgColor(@NotNull Color color) {
      putClientProperty("JButton.focusedBackgroundColor", color);
    }

    protected final void setBorderColor(@NotNull Color color) {
      putClientProperty("JButton.borderColor", color);
    }

    protected final void setFocusedBorderColor(@NotNull Color color) {
      putClientProperty("JButton.focusedBorderColor", color);
    }
  }

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

  private static void setWidth72(@NotNull JButton button) {
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

  private static class MyPluginModel extends InstalledPluginsTableModel implements PluginManagerMain.PluginEnabler {
    private final List<ListPluginComponent> myListComponents = new ArrayList<>();
    private final Map<IdeaPluginDescriptor, List<ListPluginComponent>> myListMap = new HashMap<>();
    private final Map<IdeaPluginDescriptor, List<GridCellPluginComponent>> myGridMap = new HashMap<>();
    private final List<PluginsGroup> myEnabledGroups = new ArrayList<>();
    private PluginsGroupComponent myDownloadedPanel;
    private PluginsGroup myDownloaded;
    private PluginsGroup myInstalling;
    private PluginsGroup myUpdates;
    private TopComponentController myTopController;

    private static final Set<IdeaPluginDescriptor> myInstallingPlugins = new HashSet<>();
    private static final Set<IdeaPluginDescriptor> myInstallingWithUpdatesPlugins = new HashSet<>();
    private static final Map<IdeaPluginDescriptor, InstallPluginInfo> myInstallingInfos = new HashMap<>();

    public boolean needRestart;
    public boolean createShutdownCallback = true;
    public DetailsPagePluginComponent detailPanel;

    private StatusBarEx myStatusBar;

    public MyPluginModel() {
      Window window = ProjectUtil.getActiveFrameOrWelcomeScreen();
      myStatusBar = getStatusBar(window);
      if (myStatusBar == null && window != null) {
        myStatusBar = getStatusBar(window.getOwner());
      }
    }

    @Nullable
    private static StatusBarEx getStatusBar(@Nullable Window frame) {
      if (frame instanceof IdeFrame && !(frame instanceof WelcomeFrame)) {
        return (StatusBarEx)((IdeFrame)frame).getStatusBar();
      }
      return null;
    }

    public void addComponent(@NotNull CellPluginComponent component) {
      if (component instanceof ListPluginComponent) {
        if (myInstallingPlugins.contains(component.myPlugin)) {
          return;
        }
        myListComponents.add((ListPluginComponent)component);

        List<ListPluginComponent> components = myListMap.get(component.myPlugin);
        if (components == null) {
          myListMap.put(component.myPlugin, components = new ArrayList<>());
        }
        components.add((ListPluginComponent)component);
      }
      else {
        List<GridCellPluginComponent> components = myGridMap.get(component.myPlugin);
        if (components == null) {
          myGridMap.put(component.myPlugin, components = new ArrayList<>());
        }
        components.add((GridCellPluginComponent)component);
      }
    }

    public void removeComponent(@NotNull CellPluginComponent component) {
      if (component instanceof ListPluginComponent) {
        myListComponents.remove((ListPluginComponent)component);

        List<ListPluginComponent> components = myListMap.get(component.myPlugin);
        if (components != null) {
          components.remove(component);
          if (components.isEmpty()) {
            myListMap.remove(component.myPlugin);
          }
        }
      }
      else {
        List<GridCellPluginComponent> components = myGridMap.get(component.myPlugin);
        if (components != null) {
          components.remove(component);
          if (components.isEmpty()) {
            myGridMap.remove(component.myPlugin);
          }
        }
      }
    }

    public void setTopController(@NotNull TopComponentController topController) {
      myTopController = topController;

      for (InstallPluginInfo info: myInstallingInfos.values()) {
        info.fromBackground(this);
      }
      if (!myInstallingInfos.isEmpty()) {
        myTopController.showProgress(true);
      }
    }

    @NotNull
    public static Set<IdeaPluginDescriptor> getInstallingPlugins() {
      return myInstallingPlugins;
    }

    public static boolean isInstallingOrUpdate(@NotNull IdeaPluginDescriptor descriptor) {
      return myInstallingWithUpdatesPlugins.contains(descriptor);
    }

    public void installOrUpdatePlugin(@NotNull IdeaPluginDescriptor descriptor, boolean install) {
      if (!PluginManagerMain.checkThirdPartyPluginsAllowed(Collections.singletonList(descriptor))) {
        return;
      }

      PluginNode pluginNode;
      if (descriptor instanceof PluginNode) {
        pluginNode = (PluginNode)descriptor;
      }
      else {
        pluginNode = new PluginNode(descriptor.getPluginId(), descriptor.getName(), "-1");
        pluginNode.setDepends(Arrays.asList(descriptor.getDependentPluginIds()), descriptor.getOptionalDependentPluginIds());
        pluginNode.setRepositoryName(PluginInstaller.UNKNOWN_HOST_MARKER);
      }
      List<PluginNode> pluginsToInstall = ContainerUtil.newArrayList(pluginNode);

      PluginManagerMain.suggestToEnableInstalledDependantPlugins(this, pluginsToInstall);
      needRestart = true;

      installPlugin(pluginsToInstall, getAllPlugins(), this, prepareToInstall(descriptor, install));
    }

    private static void installPlugin(@NotNull List<PluginNode> pluginsToInstall,
                                      @NotNull List<IdeaPluginDescriptor> allPlugins,
                                      @NotNull PluginManagerMain.PluginEnabler pluginEnabler,
                                      @NotNull InstallPluginInfo info) {
      ApplicationManager.getApplication().executeOnPooledThread(() -> {
        boolean cancel = false;
        boolean error = false;

        try {
          error = !PluginInstaller.prepareToInstall(pluginsToInstall, allPlugins, pluginEnabler, info.indicator);
        }
        catch (ProcessCanceledException e) {
          cancel = true;
        }
        catch (Throwable e) {
          PluginManagerMain.LOG.error(e);
          error = true;
        }

        boolean success = !error;
        boolean _cancel = cancel;
        ApplicationManager.getApplication().invokeLater(() -> info.finish(success, _cancel), ModalityState.any());
      });
    }

    public void toBackground() {
      for (InstallPluginInfo info: myInstallingInfos.values()) {
        info.toBackground(myStatusBar);
      }
    }

    @NotNull
    private InstallPluginInfo prepareToInstall(@NotNull IdeaPluginDescriptor descriptor, boolean install) {
      InstallPluginInfo info = new InstallPluginInfo(descriptor, this, install);
      myInstallingInfos.put(descriptor, info);

      if (myInstallingWithUpdatesPlugins.isEmpty()) {
        myTopController.showProgress(true);
      }
      myInstallingWithUpdatesPlugins.add(descriptor);
      if (install) {
        myInstallingPlugins.add(descriptor);
      }

      if (install && myInstalling != null) {
        if (myInstalling.ui == null) {
          myInstalling.descriptors.add(descriptor);
          myDownloadedPanel.addGroup(myInstalling, 0);
        }
        else {
          myDownloadedPanel.addToGroup(myInstalling, descriptor);
        }

        myInstalling.titleWithCount();
        myDownloadedPanel.doLayout();
      }

      List<GridCellPluginComponent> gridComponents = myGridMap.get(descriptor);
      if (gridComponents != null) {
        for (GridCellPluginComponent gridComponent: gridComponents) {
          gridComponent.showProgress();
        }
      }
      List<ListPluginComponent> listComponents = myListMap.get(descriptor);
      if (listComponents != null) {
        for (ListPluginComponent listComponent: listComponents) {
          listComponent.showProgress();
        }
      }
      if (detailPanel != null) {
        assert detailPanel.myPlugin == descriptor;
        detailPanel.showProgress();
      }

      return info;
    }

    public void finishInstall(@NotNull IdeaPluginDescriptor descriptor, boolean success) {
      InstallPluginInfo info = finishInstall(descriptor);

      if (myInstallingWithUpdatesPlugins.isEmpty()) {
        myTopController.showProgress(false);
      }

      List<GridCellPluginComponent> gridComponents = myGridMap.get(descriptor);
      if (gridComponents != null) {
        for (GridCellPluginComponent gridComponent: gridComponents) {
          gridComponent.hideProgress(success);
        }
      }
      List<ListPluginComponent> listComponents = myListMap.get(descriptor);
      if (listComponents != null) {
        for (ListPluginComponent listComponent: listComponents) {
          listComponent.hideProgress(success);
        }
      }
      if (detailPanel != null) {
        assert detailPanel.myPlugin == descriptor;
        detailPanel.hideProgress(success);
      }

      if (info.install) {
        if (myInstalling != null && myInstalling.ui != null) {
          if (myInstallingPlugins.isEmpty()) {
            myDownloadedPanel.removeGroup(myInstalling);
          }
          else {
            myDownloadedPanel.removeFromGroup(myInstalling, descriptor);
            myInstalling.titleWithCount();
          }
          myDownloadedPanel.doLayout();
        }
        if (success) {
          appendOrUpdateDescriptor(descriptor);
        }
      }
      else if (success) {
        if (myDownloaded != null && myDownloaded.ui != null) {
          PluginId pluginId = descriptor.getPluginId();
          CellPluginComponent component =
            ContainerUtil.find(myDownloaded.ui.plugins, _component -> pluginId.equals(_component.myPlugin.getPluginId()));
          if (component != null) {
            ((ListPluginComponent)component).changeUpdateToRestart();
          }
        }
        if (myUpdates != null) {
          myUpdates.titleWithCount();
        }
      }

      info.indicator.cancel();

      if (!success) {
        Messages.showErrorDialog("Plugin " + descriptor.getName() + " download or installing failed",
                                 IdeBundle.message("action.download.and.install.plugin"));
      }
    }

    @NotNull
    public static InstallPluginInfo finishInstall(@NotNull IdeaPluginDescriptor descriptor) {
      InstallPluginInfo info = myInstallingInfos.remove(descriptor);
      myInstallingWithUpdatesPlugins.remove(descriptor);
      if (info.install) {
        myInstallingPlugins.remove(descriptor);
      }
      return info;
    }

    public void addProgress(@NotNull IdeaPluginDescriptor descriptor, @NotNull ProgressIndicatorEx indicator) {
      myInstallingInfos.get(descriptor).indicator.addStateDelegate(indicator);
    }

    public void removeProgress(@NotNull IdeaPluginDescriptor descriptor, @NotNull ProgressIndicatorEx indicator) {
      myInstallingInfos.get(descriptor).indicator.removeStateDelegate(indicator);
    }

    public void addEnabledGroup(@NotNull PluginsGroup group) {
      myEnabledGroups.add(group);
    }

    public void setDownloadedGroup(@NotNull PluginsGroupComponent panel,
                                   @NotNull PluginsGroup downloaded,
                                   @NotNull PluginsGroup installing) {
      myDownloadedPanel = panel;
      myDownloaded = downloaded;
      myInstalling = installing;
    }

    public void setUpdateGroup(@NotNull PluginsGroup group) {
      myUpdates = group;
    }

    @Override
    public void appendOrUpdateDescriptor(@NotNull IdeaPluginDescriptor descriptor) {
      super.appendOrUpdateDescriptor(descriptor);
      needRestart = true;
      if (myDownloaded == null) {
        return;
      }

      if (myDownloaded.ui == null) {
        myDownloaded.descriptors.add(descriptor);
        myDownloaded.titleWithEnabled(this);

        myDownloadedPanel.addGroup(myDownloaded, myInstalling == null || myInstalling.ui == null ? 0 : 1);
        myDownloadedPanel.setSelection(myDownloaded.ui.plugins.get(0));
        myDownloadedPanel.doLayout();

        addEnabledGroup(myDownloaded);
      }
      else {
        myDownloadedPanel.addToGroup(myDownloaded, descriptor);
        myDownloaded.titleWithEnabled(this);
        myDownloadedPanel.setSelection(myDownloaded.ui.plugins.get(myDownloaded.descriptors.indexOf(descriptor)));
        myDownloadedPanel.doLayout();
      }
    }

    public boolean isEnabled(@NotNull IdeaPluginDescriptor plugin) {
      return isEnabled(plugin.getPluginId());
    }

    @NotNull
    public String getEnabledTitle(@NotNull IdeaPluginDescriptor plugin) {
      return isEnabled(plugin) ? "Disable" : "Enable";
    }

    public void changeEnableDisable(@NotNull IdeaPluginDescriptor plugin) {
      enableRows(new IdeaPluginDescriptor[]{plugin}, !isEnabled(plugin.getPluginId()));
      updateAfterEnableDisable();
    }

    public void changeEnableDisable(@NotNull IdeaPluginDescriptor[] plugins, boolean state) {
      enableRows(plugins, state);
      updateAfterEnableDisable();
    }

    @Override
    public void enablePlugins(Set<IdeaPluginDescriptor> disabled) {
      changeEnableDisable(disabled.toArray(new IdeaPluginDescriptor[0]), true);
    }

    @Override
    public void disablePlugins(Set<IdeaPluginDescriptor> disabled) {
      changeEnableDisable(disabled.toArray(new IdeaPluginDescriptor[0]), false);
    }

    public void enableRequiredPlugins(@NotNull IdeaPluginDescriptor descriptor) {
      Set<PluginId> requiredPluginIds = getRequiredPlugins(descriptor.getPluginId());
      if (ContainerUtil.isEmpty(requiredPluginIds)) {
        return;
      }

      List<IdeaPluginDescriptor> allPlugins = getAllPlugins();
      Set<IdeaPluginDescriptor> requiredPlugins = new HashSet<>();

      for (PluginId pluginId: requiredPluginIds) {
        IdeaPluginDescriptor result = ContainerUtil.find(allPlugins, d -> pluginId.equals(d.getPluginId()));
        if (result == null && PluginManagerCore.isModuleDependency(pluginId)) {
          result = ContainerUtil.find(allPlugins, d -> {
            if (d instanceof IdeaPluginDescriptorImpl) {
              List<String> modules = ((IdeaPluginDescriptorImpl)d).getModules();
              return modules != null && modules.contains(pluginId.getIdString());
            }
            return false;
          });
          if (result != null) {
            getEnabledMap().put(pluginId, Boolean.TRUE);
          }
        }
        if (result != null) {
          requiredPlugins.add(result);
        }
      }

      if (!requiredPlugins.isEmpty()) {
        enablePlugins(requiredPlugins);
      }
    }

    private void updateAfterEnableDisable() {
      for (ListPluginComponent component: myListComponents) {
        component.updateEnabledState();
      }
      for (PluginsGroup group: myEnabledGroups) {
        group.titleWithEnabled(this);
      }
    }

    public void doUninstall(@NotNull Component uiParent, @NotNull IdeaPluginDescriptor descriptor, @Nullable Runnable update) {
      if (!dependent((IdeaPluginDescriptorImpl)descriptor).isEmpty()) {
        String message = IdeBundle.message("several.plugins.depend.on.0.continue.to.remove", descriptor.getName());
        String title = IdeBundle.message("title.plugin.uninstall");
        if (Messages.showYesNoDialog(uiParent, message, title, Messages.getQuestionIcon()) != Messages.YES) {
          return;
        }
      }

      try {
        ((IdeaPluginDescriptorImpl)descriptor).setDeleted(true);
        PluginInstaller.prepareToUninstall(descriptor.getPluginId());
        needRestart |= descriptor.isEnabled();
      }
      catch (IOException e) {
        PluginManagerMain.LOG.error(e);
      }

      if (update != null) {
        update.run();
      }

      List<ListPluginComponent> listComponents = myListMap.get(descriptor);
      if (listComponents != null) {
        for (ListPluginComponent listComponent: listComponents) {
          listComponent.updateAfterUninstall();
        }
      }

      for (ListPluginComponent component: myListComponents) {
        component.updateErrors();
      }
    }

    public boolean hasErrors(@NotNull IdeaPluginDescriptor plugin) {
      return PluginManagerCore.isIncompatible(plugin) || hasProblematicDependencies(plugin.getPluginId());
    }
  }

  private static class InstallPluginInfo {
    public final MyBgProgressIndicator indicator = new MyBgProgressIndicator();
    private final IdeaPluginDescriptor myDescriptor;
    private MyPluginModel myPluginModel;
    public final boolean install;
    private TaskInfo myStatusBarTaskInfo;

    public InstallPluginInfo(@NotNull IdeaPluginDescriptor descriptor, @NotNull MyPluginModel pluginModel, boolean install) {
      myDescriptor = descriptor;
      myPluginModel = pluginModel;
      this.install = install;
    }

    public synchronized void toBackground(@Nullable StatusBarEx statusBar) {
      myPluginModel = null;
      indicator.removeStateDelegate(null);
      if (statusBar != null) {
        statusBar.addProgress(indicator, myStatusBarTaskInfo = MyProgressIndicator.task());
      }
    }

    public synchronized void fromBackground(@NotNull MyPluginModel pluginModel) {
      myPluginModel = pluginModel;
      closeStatusBarIndicator();
    }

    public synchronized void finish(boolean success, boolean cancel) {
      if (myPluginModel == null) {
        MyPluginModel.finishInstall(myDescriptor);
        closeStatusBarIndicator();
      }
      else if (!cancel) {
        myPluginModel.finishInstall(myDescriptor, success);
      }
    }

    private void closeStatusBarIndicator() {
      if (myStatusBarTaskInfo != null) {
        indicator.finish(myStatusBarTaskInfo);
        myStatusBarTaskInfo = null;
      }
    }
  }

  private static class MyTwoLineProgressIndicator extends MyProgressIndicator {
    @Override
    protected void createCompactTextAndProgress() {
      JPanel textWrapper = new NonOpaquePanel(new BorderLayout());
      textWrapper.add(myText, BorderLayout.CENTER);
      myText.recomputeSize();

      NonOpaquePanel progressWrapper = new NonOpaquePanel(new BorderLayout());
      progressWrapper.setBorder(JBUI.Borders.emptyRight(4));
      progressWrapper.add(myProgress, BorderLayout.CENTER);

      JComponent component = getComponent();
      component.add(textWrapper, BorderLayout.NORTH);
      component.add(progressWrapper, BorderLayout.CENTER);
    }
  }

  private static class MyProgressIndicator extends InlineProgressIndicator {
    private Runnable myCancelRunnable;

    public MyProgressIndicator() {
      super(true, task());

      myText.putClientProperty("NoFillPanelColorForDarcula", Boolean.TRUE);
      updateProgressNow();
      getComponent().setToolTipText(null);
    }

    public void setCancelRunnable(@NotNull Runnable runnable) {
      myCancelRunnable = runnable;
    }

    @Override
    protected void cancelRequest() {
      super.cancelRequest();
      myCancelRunnable.run();
    }

    @NotNull
    public JComponent createBaselineWrapper() {
      return new Wrapper(getComponent()) {
        @Override
        public int getBaseline(int width, int height) {
          return (int)(height * 0.85);
        }
      };
    }

    @NotNull
    private static TaskInfo task() {
      return new Task.Modal(null, "Downloading...", true) {
        @Override
        public void run(@NotNull ProgressIndicator indicator) {
          // TODO: Auto-generated method stub
        }
      };
    }
  }

  private static class MyBgProgressIndicator extends AbstractProgressIndicatorExBase {
    public MyBgProgressIndicator() {
      setText("Downloading...");
      setIndeterminate(false);
    }

    public void removeStateDelegate(@Nullable ProgressIndicatorEx delegate) {
      List<ProgressIndicatorEx> stateDelegates =
        ReflectionUtil.getField(AbstractProgressIndicatorExBase.class, this, List.class, "myStateDelegates");
      synchronized (this) {
        if (delegate == null) {
          stateDelegates.clear();
        }
        else {
          stateDelegates.remove(delegate);
        }
      }
    }
  }

  private static boolean isJBPlugin(@NotNull IdeaPluginDescriptor plugin) {
    return plugin.isBundled() || PluginManagerMain.isDevelopedByJetBrains(plugin);
  }

  @Nullable
  private static String getShortDescription(@NotNull IdeaPluginDescriptor plugin, boolean shortSize) {
    return PluginSiteUtils.preparePluginDescription(plugin.getDescription(), shortSize);
  }

  public static class PluginSiteUtils {
    private static final Pattern TAG_PATTERN =
      Pattern.compile("</?\\w+((\\s+\\w+(\\s*=\\s*(?:\".*?\"|'.*?'|[\\^'\">\\s]+))?)+\\s*|\\s*)/?>");
    private static final int SHORT_DESC_SIZE = 170;
    private static final Pattern BR_PATTERN = Pattern.compile("<br\\s*/?>");

    @Nullable
    public static String preparePluginDescription(@Nullable String s, boolean shortSize) {
      if (s == null || s.isEmpty()) {
        return null;
      }
      String description = prepareDescription(s, shortSize);
      return description.isEmpty() || description.endsWith(".") ? description : description + ".";
    }

    @NotNull
    private static String prepareDescription(@NotNull String s, boolean shortSize) {
      if (shortSize) {
        String[] split = BR_PATTERN.split(s);

        if (split.length > 1) {
          String sanitize = stripTags(split[0]);
          if (sanitize.length() <= SHORT_DESC_SIZE) return sanitize;
        }
      }

      String stripped = stripTags(s);

      if (shortSize) {
        for (String sep: new String[]{". ", ".\n", ": ", ":\n"}) {
          String by = substringBy(stripped, sep);
          if (by != null) return by;
        }

        if (stripped.length() > SHORT_DESC_SIZE) {
          stripped = stripped.substring(0, SHORT_DESC_SIZE);

          int index = stripped.lastIndexOf(' ');
          if (index == -1) {
            index = stripped.length();
          }

          stripped = stripped.substring(0, index) + "...";
        }
      }

      return stripped;
    }

    @Nullable
    private static String substringBy(@NotNull String str, @NotNull String separator) {
      int end = str.indexOf(separator);
      if (end > 0 && end <= SHORT_DESC_SIZE) {
        return str.substring(0, end + (separator.contains(":") ? 0 : separator.length())).trim();
      }
      return null;
    }

    @NotNull
    private static String stripTags(@NotNull String s) {
      return TAG_PATTERN.matcher(s).replaceAll("").trim();
    }
  }

  private static final class PluginLogoInfo {
    private static LayeredIcon PluginLogoJB_40;
    private static LayeredIcon PluginLogoError_40;
    private static LayeredIcon PluginLogoJBError_40;

    private static LayeredIcon PluginLogoDisabledJB_40;
    private static LayeredIcon PluginLogoDisabledError_40;
    private static LayeredIcon PluginLogoDisabledJBError_40;

    private static LayeredIcon PluginLogoJB_80;
    private static LayeredIcon PluginLogoError_80;
    private static LayeredIcon PluginLogoJBError_80;

    private static LayeredIcon PluginLogoDisabledJB_80;
    private static LayeredIcon PluginLogoDisabledError_80;
    private static LayeredIcon PluginLogoDisabledJBError_80;

    private static boolean myCreateIcons = true;

    static {
      LafManager.getInstance().addLafManagerListener(source -> myCreateIcons = true);
    }

    private static void createIcons() {
      if (!myCreateIcons) {
        return;
      }
      myCreateIcons = false;

      setSouthEast(PluginLogoJB_40 = new LayeredIcon(2), AllIcons.Plugins.PluginLogo_40, AllIcons.Plugins.ModifierJBLogo);
      setSouthWest(PluginLogoError_40 = new LayeredIcon(2), AllIcons.Plugins.PluginLogo_40, AllIcons.Plugins.ModifierInvalid);
      setSouthEastWest(PluginLogoJBError_40 = new LayeredIcon(3), AllIcons.Plugins.PluginLogo_40, AllIcons.Plugins.ModifierJBLogo,
                       AllIcons.Plugins.ModifierInvalid);

      Icon disabledJBLogo = IconLoader.getDisabledIcon(AllIcons.Plugins.ModifierJBLogo);
      assert disabledJBLogo != null;

      setSouthEast(PluginLogoDisabledJB_40 = new LayeredIcon(2), AllIcons.Plugins.PluginLogoDisabled_40, disabledJBLogo);
      setSouthWest(PluginLogoDisabledError_40 = new LayeredIcon(2), AllIcons.Plugins.PluginLogoDisabled_40,
                   AllIcons.Plugins.ModifierInvalid);
      setSouthEastWest(PluginLogoDisabledJBError_40 = new LayeredIcon(3), AllIcons.Plugins.PluginLogoDisabled_40, disabledJBLogo,
                       AllIcons.Plugins.ModifierInvalid);

      Icon jbLogo2x = IconUtil.scale(AllIcons.Plugins.ModifierJBLogo, null, 2);
      Icon errorLogo2x = IconUtil.scale(AllIcons.Plugins.ModifierInvalid, null, 2);

      setSouthEast(PluginLogoJB_80 = new LayeredIcon(2), AllIcons.Plugins.PluginLogo_80, jbLogo2x);
      setSouthWest(PluginLogoError_80 = new LayeredIcon(2), AllIcons.Plugins.PluginLogo_80, errorLogo2x);
      setSouthEastWest(PluginLogoJBError_80 = new LayeredIcon(3), AllIcons.Plugins.PluginLogo_80, jbLogo2x, errorLogo2x);

      Icon disabledJBLogo2x = IconLoader.getDisabledIcon(jbLogo2x);
      assert disabledJBLogo2x != null;

      setSouthEast(PluginLogoDisabledJB_80 = new LayeredIcon(2), AllIcons.Plugins.PluginLogoDisabled_80, disabledJBLogo2x);
      setSouthWest(PluginLogoDisabledError_80 = new LayeredIcon(2), AllIcons.Plugins.PluginLogoDisabled_80, errorLogo2x);
      setSouthEastWest(PluginLogoDisabledJBError_80 = new LayeredIcon(3), AllIcons.Plugins.PluginLogoDisabled_80, disabledJBLogo2x,
                       errorLogo2x);
    }

    private static void setSouthEast(@NotNull LayeredIcon layeredIcon, @NotNull Icon main, @NotNull Icon southEast) {
      layeredIcon.setIcon(main, 0);
      layeredIcon.setIcon(southEast, 1, SwingConstants.SOUTH_EAST);
    }

    private static void setSouthWest(@NotNull LayeredIcon layeredIcon, @NotNull Icon main, @NotNull Icon southWest) {
      layeredIcon.setIcon(main, 0);
      layeredIcon.setIcon(southWest, 1, SwingConstants.SOUTH_WEST);
    }

    private static void setSouthEastWest(@NotNull LayeredIcon layeredIcon,
                                         @NotNull Icon main,
                                         @NotNull Icon southEast,
                                         @NotNull Icon southWest) {
      layeredIcon.setIcon(main, 0);
      layeredIcon.setIcon(southEast, 1, SwingConstants.SOUTH_EAST);
      layeredIcon.setIcon(southWest, 2, SwingConstants.SOUTH_WEST);
    }

    @NotNull
    public static Icon getIcon(boolean big, boolean jb, boolean error, boolean disabled) {
      createIcons();

      if (jb && !error) {
        if (big) {
          return disabled ? PluginLogoDisabledJB_80 : PluginLogoJB_80;
        }
        return disabled ? PluginLogoDisabledJB_40 : PluginLogoJB_40;
      }
      if (!jb && error) {
        if (big) {
          return disabled ? PluginLogoDisabledError_80 : PluginLogoError_80;
        }
        return disabled ? PluginLogoDisabledError_40 : PluginLogoError_40;
      }
      if (jb/* && error*/) {
        if (big) {
          return disabled ? PluginLogoDisabledJBError_80 : PluginLogoJBError_80;
        }
        return disabled ? PluginLogoDisabledJBError_40 : PluginLogoJBError_40;
      }
      // !jb && !error
      if (big) {
        return disabled ? AllIcons.Plugins.PluginLogoDisabled_80 : AllIcons.Plugins.PluginLogo_80;
      }
      return disabled ? AllIcons.Plugins.PluginLogoDisabled_40 : AllIcons.Plugins.PluginLogo_40;
    }
  }
}