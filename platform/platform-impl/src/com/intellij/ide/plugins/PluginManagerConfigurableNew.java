// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins;

import com.intellij.icons.AllIcons;
import com.intellij.ide.HelpTooltip;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.application.impl.ApplicationImpl;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.OptionalConfigurable;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.options.newEditor.SettingsDialog;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.*;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.components.JBOptionButton;
import com.intellij.ui.components.JBPanelWithEmptyText;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextField;
import com.intellij.ui.components.breadcrumbs.Breadcrumbs;
import com.intellij.ui.components.labels.LinkLabel;
import com.intellij.ui.components.labels.LinkListener;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.ui.components.panels.OpaquePanel;
import com.intellij.util.BooleanFunction;
import com.intellij.util.Function;
import com.intellij.util.IconUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.net.HttpConfigurable;
import com.intellij.util.ui.*;
import com.intellij.xml.util.XmlStringUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import sun.swing.SwingUtilities2;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.text.BadLocationException;
import javax.swing.text.View;
import java.awt.*;
import java.awt.event.*;
import java.io.IOException;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;
import java.util.Map.Entry;
import java.util.regex.Pattern;

import static com.intellij.ide.plugins.PluginManagerCore.getPlugins;

/**
 * @author Alexander Lobas
 */
public class PluginManagerConfigurableNew
  implements SearchableConfigurable, Configurable.NoScroll, Configurable.NoMargin, Configurable.TopComponentProvider, OptionalConfigurable {
  public static final String ID = "preferences.pluginManager2";

  private static final String SELECTION_TAB_KEY = "PluginConfigurable.selectionTab";

  private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("MMM dd, yyyy");
  private static final DecimalFormat K_FORMAT = new DecimalFormat("###.#K");
  private static final DecimalFormat M_FORMAT = new DecimalFormat("###.#M");

  @SuppressWarnings("UseJBColor")
  private static final Color MAIN_BG_COLOR = new JBColor(() -> JBColor.isBright() ? UIUtil.getListBackground() : new Color(0x313335));

  private final TagBuilder myTagBuilder;

  private boolean myShowDetailPanel;
  private LinkListener<Pair<IdeaPluginDescriptor, Integer>> myNameListener;

  private CardLayoutPanel<Object, Object, JComponent> myCardPanel;
  private TabHeaderComponent myTabHeaderComponent;
  private TopComponentController myTopController;
  private final SearchTextField mySearchTextField;

  private final MyPluginModel myPluginsModel = new MyPluginModel();

  private Runnable myShutdownCallback;

  public PluginManagerConfigurableNew() {
    myTagBuilder = new TagBuilder() {
      @NotNull
      @Override
      public TagComponent createTagComponent(@NotNull String tag) {
        Color color;
        String tooltip = null;
        if ("graphics".equals(tag)) {
          color = new JBColor(0xEFE4CE, 0x5E584B);
        }
        else if ("misc".equals(tag)) {
          color = new JBColor(0xD8EDF8, 0x49606E);
        }
        else if ("EAP".equals(tag)) {
          color = new JBColor(0xF2D2CF, 0xF2D2CF);
          tooltip = "The EAP version does not guarantee the stability\nand availability of the plugin.";
        }
        else {
          color = new JBColor(0xEAEAEC, 0x4D4D4D);
        }

        return new TagComponent(tag, tooltip, color);
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
    };
    mySearchTextField.getTextEditor().putClientProperty("JTextField.Search.Gap", JBUI.scale(8));
    mySearchTextField.getTextEditor().setBackground(MAIN_BG_COLOR);
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
        // TODO: Auto-generated method stub
      }
    });
    actions.add(new DumbAwareAction("Install Plugin from Disk...") {
      @Override
      public void actionPerformed(AnActionEvent e) {
        // TODO: Auto-generated method stub
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

    JBTextField editor = mySearchTextField.getTextEditor();
    editor.getEmptyText().appendText("Search plugins");
    editor.putClientProperty("StatusVisibleFunction", (BooleanFunction<JBTextField>)field -> field.getText().isEmpty());
    editor.setOpaque(true);
    editor.setBorder(JBUI.Borders.empty(0, 25));
    mySearchTextField.setBorder(JBUI.Borders.customLine(new JBColor(0xC5C5C5, 0x515151)));
    panel.add(mySearchTextField, BorderLayout.NORTH);

    myNameListener = (aSource, data) -> {
      myShowDetailPanel = true;

      JButton backButton = new JButton("Plugins");
      configureBackButton(backButton);

      ActionListener listener = event -> {
        removeDetailsPanel();
        myCardPanel.select(data.second, true);
        storeSelectionTab(data.second);
        myTabHeaderComponent.setSelection(data.second);
      };
      backButton.addActionListener(listener);
      if (SystemInfo.isMac) {
        backButton.registerKeyboardAction(listener, KeyStroke.getKeyStroke('[', InputEvent.META_MASK), JComponent.WHEN_IN_FOCUSED_WINDOW);
        backButton.registerKeyboardAction(listener, KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, InputEvent.META_MASK),
                                          JComponent.WHEN_IN_FOCUSED_WINDOW);
      }
      else {
        backButton.registerKeyboardAction(listener, KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, InputEvent.ALT_MASK),
                                          JComponent.WHEN_IN_FOCUSED_WINDOW);
        backButton.registerKeyboardAction(listener, KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, InputEvent.CTRL_MASK | InputEvent.ALT_MASK),
                                          JComponent.WHEN_IN_FOCUSED_WINDOW);
      }

      myTopController.setLeftComponent(backButton);
      myCardPanel.select(data, true);
      myTabHeaderComponent.setSelection(-1);
    };

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
            return createTrendingPanel(myNameListener);
          }
          if (index == 1) {
            return createInstalledPanel(myNameListener);
          }
          if (index == 2) {
            return createUpdatesPanel(myNameListener);
          }
        }

        //noinspection ConstantConditions,unchecked
        Pair<IdeaPluginDescriptor, Integer> data = (Pair<IdeaPluginDescriptor, Integer>)key;
        return new DetailsPageListComponent(data.first, data.second == 2);
      }
    };
    panel.add(myCardPanel);

    myTabHeaderComponent = new TabHeaderComponent(actions, index -> {
      if (myShowDetailPanel) {
        removeDetailsPanel();
      }
      myCardPanel.select(index, true);
      storeSelectionTab(index);
    });

    myTabHeaderComponent.addTab("Trending");
    myTabHeaderComponent.addTab("Installed");
    myTabHeaderComponent.addTab(() -> "Updates (" + 5 + ")");

    int selectionTab = getStoredSelectionTab();
    myTabHeaderComponent.setSelection(selectionTab);
    myCardPanel.select(selectionTab, true);

    return panel;
  }

  private void removeDetailsPanel() {
    myShowDetailPanel = false;
    myTopController.setLeftComponent(null);
    myCardPanel.remove(myCardPanel.getComponentCount() - 1);
  }

  private static int getStoredSelectionTab() {
    return PropertiesComponent.getInstance().getInt(SELECTION_TAB_KEY, 0);
  }

  private static void storeSelectionTab(int value) {
    PropertiesComponent.getInstance().setValue(SELECTION_TAB_KEY, value, 0);
  }

  @Override
  public void disposeUIResources() {
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
    return myTabHeaderComponent;
  }

  @NotNull
  private JComponent createTrendingPanel(@NotNull LinkListener<Pair<IdeaPluginDescriptor, Integer>> listener) {
    PluginsGroupComponent panel =
      new PluginsGroupComponent(new PluginsGridLayout(), (aSource, aLinkData) -> listener.linkSelected(aSource, new Pair<>(aLinkData, 0)),
                                descriptor -> new GridCellPluginComponent(myPluginsModel, descriptor, myTagBuilder));
    panel.getEmptyText().setText("Trending plugins are not loaded.")
         .appendSecondaryText("Check the interner connection.", StatusText.DEFAULT_ATTRIBUTES, null);

    try {
      List<IdeaPluginDescriptor> list = RepositoryHelper.loadCachedPlugins();
      String[] groups = {"Featured", "New and Updated", "Top Downloads", "Top Rated"};
      int groupSize = 6;

      if (list != null && list.size() >= groups.length * groupSize) {
        int index = 0;
        for (String name : groups) {
          PluginsGroup group = new PluginsGroup();
          group.title = name;
          group.rightAction = new LinkLabel("Show All", null);
          for (int i = 0; i < groupSize; i++) {
            group.descriptors.add(list.get(index++));
          }

          panel.addGroup(group);
        }
      }
    }
    catch (IOException e) {
      e.printStackTrace();
    }

    return createScrollPane(panel);
  }

  @NotNull
  private JComponent createInstalledPanel(@NotNull LinkListener<Pair<IdeaPluginDescriptor, Integer>> listener) {
    PluginsGroupComponent panel = new PluginsGroupComponent(new PluginsListLayout(), (aSource, aLinkData) -> listener
      .linkSelected(aSource, new Pair<>(aLinkData, 1)), descriptor -> new ListPluginComponent(myPluginsModel, descriptor, false));

    PluginsGroup downloaded = new PluginsGroup();
    PluginsGroup bundled = new PluginsGroup();

    downloaded.descriptors.addAll(InstalledPluginsState.getInstance().getInstalledPlugins());

    ApplicationInfoEx appInfo = ApplicationInfoEx.getInstanceEx();
    int bundledEnabled = 0;
    int downloadedEnabled = 0;

    for (IdeaPluginDescriptor descriptor : getPlugins()) {
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
      downloaded.titleWithCount("Downloaded", downloadedEnabled);
      panel.addGroup(downloaded);
    }

    bundled.sortByName();
    bundled.titleWithCount("Bundled", bundledEnabled);
    panel.addGroup(bundled);
    myPluginsModel.setEnabledGroup(bundled);

    return createScrollPane(panel);
  }

  @NotNull
  private JComponent createUpdatesPanel(@NotNull LinkListener<Pair<IdeaPluginDescriptor, Integer>> listener) {
    PluginsGroupComponent panel = new PluginsGroupComponent(new PluginsListLayout(), (aSource, aLinkData) -> listener
      .linkSelected(aSource, new Pair<>(aLinkData, 2)), descriptor -> new ListPluginComponent(myPluginsModel, descriptor, true));
    panel.getEmptyText().setText("No updates available.");

    try {
      List<IdeaPluginDescriptor> list = RepositoryHelper.loadCachedPlugins();

      if (list != null) {
        PluginsGroup group = new PluginsGroup();
        group.rightAction = new LinkLabel("Update All", null);

        for (int i = list.size() - 1; i > 0; i--) {
          IdeaPluginDescriptor descriptor = list.get(i);
          if (!StringUtil.isEmptyOrSpaces(descriptor.getChangeNotes())) {
            group.descriptors.add(descriptor);
            if (group.descriptors.size() > 9) {
              break;
            }
          }
        }

        if (!group.descriptors.isEmpty()) {
          group.sortByName();
          group.titleWithCount("Available Updates");
          panel.addGroup(group);
        }
      }
    }
    catch (IOException e) {
      e.printStackTrace();
    }

    return createScrollPane(panel);
  }

  @Override
  public boolean needDisplay() {
    return Registry.is("show.new.plugin.page");  // TODO: temp code for show page over registry
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
  private static JComponent createScrollPane(JComponent panel) {
    JBScrollPane pane = new JBScrollPane(panel);
    pane.setBorder(JBUI.Borders.empty());
    ApplicationManager.getApplication().invokeLater(() -> {
      pane.getHorizontalScrollBar().setValue(0);
      pane.getVerticalScrollBar().setValue(0);
    });
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
          for (IdeaPluginDescriptor descriptor : getPlugins()) {
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
    String category = plugin.getCategory();
    if (StringUtil.isEmptyOrSpaces(category)) {
      return Collections.emptyList();
    }
    List<String> tags = ContainerUtil.newArrayList(category.toLowerCase(Locale.US));
    if (plugin.getName().length() < 10) {
      tags.add("languages");
    }
    else if (plugin.getName().equals("SystemProperties")) {
      tags.add("EAP");
    }
    if (tags.remove("EAP")) {
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
      mySizeInfo = null;
      revalidate();
      repaint();
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

  private static class SizeInfo {
    public int width;

    public Rectangle[] tabs;
    public int[] tabTitleX;

    public int toolbarX;
  }

  private static class PluginsGroupComponent extends JBPanelWithEmptyText {
    private final LinkListener<IdeaPluginDescriptor> myListener;
    private final Function<IdeaPluginDescriptor, CellPluginComponent> myFunction;
    private final List<UIPluginGroup> myGroups = new ArrayList<>();
    private CellPluginComponent myHoverComponent;
    private CellPluginComponent mySelectionComponent;
    private final MouseAdapter myMouseHandler;

    public PluginsGroupComponent(@NotNull LayoutManager layout,
                                 @NotNull LinkListener<IdeaPluginDescriptor> listener,
                                 @NotNull Function<IdeaPluginDescriptor, CellPluginComponent> function) {
      super(layout);
      myListener = listener;
      myFunction = function;

      setOpaque(true);
      setBackground(MAIN_BG_COLOR);

      myMouseHandler = new MouseAdapter() {
        @Override
        public void mouseClicked(MouseEvent event) {
          if (SwingUtilities.isLeftMouseButton(event)) {
            CellPluginComponent component = CellPluginComponent.get(event);
            if (component != mySelectionComponent) {
              if (mySelectionComponent != null) {
                mySelectionComponent.setSelection(SelectionType.NONE);
              }
              if (component == myHoverComponent) {
                myHoverComponent = null;
              }

              mySelectionComponent = component;
              component.setSelection(SelectionType.SELECTION);
            }
          }
        }

        @Override
        public void mouseExited(MouseEvent event) {
          if (myHoverComponent != null) {
            myHoverComponent.setSelection(SelectionType.NONE);
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
    }

    public void clear() {
      myGroups.forEach(group -> group.plugins.forEach(plugin -> plugin.removeMouseListeners(myMouseHandler)));

      myGroups.clear();
      removeAll();
      myHoverComponent = mySelectionComponent = null;
    }

    public void addGroup(@NotNull PluginsGroup group) {
      UIPluginGroup uiGroup = new UIPluginGroup();
      myGroups.add(uiGroup);

      OpaquePanel panel = new OpaquePanel(new BorderLayout(), new JBColor(0xF7F7F7, 0x3D3F41));
      panel.setBorder(JBUI.Borders.empty(4, 13));

      JLabel title = new JLabel(group.title);
      title.setForeground(new JBColor(0x787878, 0x999999));
      panel.add(title, BorderLayout.WEST);
      group.titleLabel = title;

      if (group.rightAction != null) {
        panel.add(group.rightAction, BorderLayout.EAST);
      }

      add(panel);
      uiGroup.panel = panel;

      for (IdeaPluginDescriptor descriptor : group.descriptors) {
        CellPluginComponent pluginComponent = myFunction.fun(descriptor);
        uiGroup.plugins.add(pluginComponent);
        add(pluginComponent);
        pluginComponent.addMouseListeners(myMouseHandler);
        pluginComponent.setLinkListener(myListener);
      }
    }
  }

  private static class PluginsListLayout extends AbstractLayoutManager {
    private int myLineHeight;

    @Override
    public Dimension preferredLayoutSize(@NotNull Container parent) {
      calculateLineHeight(parent);

      List<UIPluginGroup> groups = ((PluginsGroupComponent)parent).myGroups;
      int height = 0;

      for (UIPluginGroup group : groups) {
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

      for (UIPluginGroup group : groups) {
        Component component = group.panel;
        int height = component.getPreferredSize().height;
        component.setBounds(0, y, width, height);
        y += height;

        for (CellPluginComponent plugin : group.plugins) {
          plugin.setBounds(0, y, width, myLineHeight);
          y += myLineHeight;
        }
      }
    }

    private void calculateLineHeight(@NotNull Container parent) {
      List<UIPluginGroup> groups = ((PluginsGroupComponent)parent).myGroups;
      int width = getParentWidth(parent) - parent.getInsets().right;

      myLineHeight = 0;

      for (UIPluginGroup group : groups) {
        for (CellPluginComponent plugin : group.plugins) {
          JEditorPane description = plugin.myDescription;
          if (description == null) {
            continue;
          }

          plugin.doLayout();
          int parentWidth = width - SwingUtilities.convertPoint(description.getParent(), description.getLocation(), plugin).x;
          if (parentWidth > 0) {
            description.putClientProperty("parent.width", new Integer(parentWidth));
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

      for (UIPluginGroup group : groups) {
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

      for (UIPluginGroup group : groups) {
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

      for (UIPluginGroup group : groups) {
        for (CellPluginComponent plugin : group.plugins) {
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

  private static class UIPluginGroup {
    public Component panel;
    public List<CellPluginComponent> plugins = new ArrayList<>();
  }

  private static class PluginsGroup {
    public String title;
    public JLabel titleLabel;
    public LinkLabel rightAction;
    public List<IdeaPluginDescriptor> descriptors = new ArrayList<>();

    public void titleWithCount(@NotNull String text) {
      title = text + " (" + descriptors.size() + ")";
    }

    public void titleWithCount(@NotNull String text, int enabled) {
      title = text + " (" + enabled + " of " + descriptors.size() + " enabled)";
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
    protected final IdeaPluginDescriptor myPlugin;

    protected LinkLabel myIconLabel;
    protected LinkLabel myName;
    protected JEditorPane myDescription;

    protected SelectionType mySelection = SelectionType.NONE;

    protected final List<Component> myMouseComponents = new ArrayList<>();

    protected CellPluginComponent(@NotNull IdeaPluginDescriptor plugin) {
      myPlugin = plugin;
      myMouseComponents.add(this);
    }

    @NotNull
    protected JPanel createPanel(@NotNull JPanel parent, @Nullable JPanel centerPanel, int hgap, int offset, int width) {
      parent.setLayout(new BorderLayout(hgap, 0));

      myIconLabel = new LinkLabel(null, AllIcons.Plugins.PluginLogo_40);
      myIconLabel.setVerticalAlignment(SwingConstants.TOP);
      myIconLabel.setOpaque(false);
      parent.add(myIconLabel, BorderLayout.WEST);
      myMouseComponents.add(myIconLabel);

      if (centerPanel == null) {
        centerPanel = new NonOpaquePanel();
      }
      centerPanel.setLayout(new VerticalLayout(offset, width));
      parent.add(centerPanel);

      setOpaque(true);
      setBorder(JBUI.Borders.empty(10));

      return centerPanel;
    }

    protected void addNameComponent(@NotNull JPanel parent, @Nullable Object constraints) {
      myName = new LinkComponent();
      myName.setText(myPlugin.getName());
      parent.add(RelativeFont.BOLD.install(myName), constraints);
      myMouseComponents.add(myName);
    }

    protected void updateIcon(boolean errors, boolean disabled) {
      boolean jb = myPlugin.isBundled() || PluginManagerMain.isDevelopedByJetBrains(myPlugin);

      myIconLabel.setIcon(PluginLogoInfo.getIcon(false, jb, errors, false));
      if (disabled) {
        myIconLabel.setDisabledIcon(PluginLogoInfo.getIcon(false, jb, errors, true));
      }
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

      parent.add(installTiny(myDescription));
      myMouseComponents.add(myDescription);

      if (myDescription.getCaret() != null) {
        myDescription.setCaretPosition(0);
      }
    }

    @NotNull
    public SelectionType getSelection() {
      return mySelection;
    }

    private static final Color HOVER_COLOR = new JBColor(0xE9EEF5, 0x464A4D);
    private static final Color GRAY_COLOR = new JBColor(Gray._130, Gray._120);

    public void setSelection(@NotNull SelectionType type) {
      mySelection = type;

      updateColors(GRAY_COLOR, type == SelectionType.NONE ? MAIN_BG_COLOR : HOVER_COLOR);
      repaint();
    }

    protected void updateColors(@NotNull Color grayedFg, @NotNull Color background) {
      setBackground(background);

      if (myDescription != null) {
        myDescription.setForeground(grayedFg);
      }
    }

    public void setLinkListener(@NotNull LinkListener<IdeaPluginDescriptor> listener) {
      //noinspection unchecked
      myIconLabel.setListener(listener, myPlugin);
      //noinspection unchecked
      myName.setListener(listener, myPlugin);
    }

    public void addMouseListeners(@NotNull MouseAdapter listener) {
      for (Component component : myMouseComponents) {
        addMouseListeners(component, listener);
      }
    }

    public void removeMouseListeners(@NotNull MouseAdapter listener) {
      for (Component component : myMouseComponents) {
        removeMouseListeners(component, listener);
      }
    }

    protected static void addMouseListeners(@NotNull Component component, @NotNull MouseAdapter listener) {
      component.addMouseListener(listener);
      component.addMouseMotionListener(listener);
    }

    protected static void removeMouseListeners(@NotNull Component component, @NotNull MouseAdapter listener) {
      component.removeMouseListener(listener);
      component.removeMouseMotionListener(listener);
    }

    @NotNull
    public static CellPluginComponent get(@NotNull MouseEvent event) {
      //noinspection ConstantConditions
      return UIUtil.getParentOfType(CellPluginComponent.class, event.getComponent());
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
    private final MyPluginModel myPluginsModel;

    private JLabel myVersion;
    private JLabel myLastUpdated;
    private JButton myUpdateButton;
    private JButton myEnableDisableButton;
    private RestartButton myRestartButton;
    private JBOptionButton myEnableDisableUninstallButton;
    private final JPanel myNameButtons;
    private JPanel myVersionPanel;
    private JPanel myErrorPanel;

    public ListPluginComponent(@NotNull MyPluginModel pluginsModel, @NotNull IdeaPluginDescriptor plugin, boolean update) {
      super(plugin);
      myPluginsModel = pluginsModel;
      pluginsModel.addComponent(this);

      JPanel centerPanel = createPanel(this, createTopShiftPanel(), offset5(), 0, 0);

      myNameButtons = createTopShiftPanel();
      myNameButtons.setLayout(new BorderLayout() {
        @Override
        public Dimension preferredLayoutSize(Container target) {
          Dimension size = super.preferredLayoutSize(target);
          size.height = myName == null ? size.height : myName.getPreferredSize().height + JBUI.scale(6);
          return size;
        }
      });
      myNameButtons.add(createButtons(update), BorderLayout.EAST);
      centerPanel.add(myNameButtons, VerticalLayout.FILL_HORIZONTAL);

      addNameComponent(myNameButtons, BorderLayout.WEST);
      myName.setVerticalAlignment(SwingConstants.TOP);

      if (update) {
        addDescriptionComponent(centerPanel, getChangeNotes(plugin), new LineFunction(3, false));
      }
      else {
        addDescriptionComponent(centerPanel, getShortDescription(plugin, false), new LineFunction(1, true));
      }

      createVersion(update);
      updateErrors();

      setSelection(SelectionType.NONE);
    }

    @NotNull
    private JPanel createButtons(boolean update) {
      JPanel buttons = createTopShiftPanel();
      buttons.setLayout(createNameBaselineLayout(6));
      if (myPlugin instanceof IdeaPluginDescriptorImpl && ((IdeaPluginDescriptorImpl)myPlugin).isDeleted()) {
        myRestartButton = new RestartButton(myPluginsModel);
        buttons.add(myRestartButton);
        myMouseComponents.add(myRestartButton);
      }
      else {
        if (update) {
          myUpdateButton = new UpdateButton();
          buttons.add(myUpdateButton);
          myMouseComponents.add(myUpdateButton);
        }
        if (myPlugin.isBundled()) {
          myEnableDisableButton = new JButton(getEnabledTitle());
          myEnableDisableButton.addActionListener(e -> myPluginsModel.changeEnableDisable(myPlugin));
          setWidth72(myEnableDisableButton);
          buttons.add(myEnableDisableButton);
          myMouseComponents.add(myEnableDisableButton);
        }
        else {
          AbstractAction enableDisableAction = new AbstractAction(getEnabledTitle()) {
            @Override
            public void actionPerformed(ActionEvent e) {
              myPluginsModel.changeEnableDisable(myPlugin);
            }
          };
          AbstractAction uninstallAction = new AbstractAction("Uninstall") {
            @Override
            public void actionPerformed(ActionEvent e) {
              myPluginsModel.doUninstall(ListPluginComponent.this, myPlugin, null);
            }
          };
          myEnableDisableUninstallButton = new MyOptionButton(enableDisableAction, uninstallAction);
          buttons.add(myEnableDisableUninstallButton);
        }
      }
      return buttons;
    }

    @NotNull
    private JPanel createNameBaselinePanel() {
      JPanel panel = new NonOpaquePanel(createNameBaselineLayout(8));
      panel.setBorder(JBUI.Borders.emptyLeft(8));
      return panel;
    }

    @NotNull
    private JPanel createTopShiftPanel() {
      JPanel panel = new NonOpaquePanel() {
        @Override
        public void setBounds(int x, int y, int width, int height) {
          super.setBounds(x, y - JBUI.scale(10), width, height + JBUI.scale(20));
        }
      };
      panel.setBorder(JBUI.Borders.emptyTop(10));
      return panel;
    }

    @NotNull
    private LayoutManager createNameBaselineLayout(int offset) {
      return new HorizontalLayout(JBUI.scale(offset)) {
        @Override
        public void layoutContainer(Container parent) {
          Insets insets = parent.getInsets();
          int x = insets.left;
          int y = insets.top + myName.getBaseline(myName.getWidth(), myName.getHeight());
          int count = parent.getComponentCount();

          for (int i = 0; i < count; i++) {
            Component component = parent.getComponent(i);
            Dimension size = component.getPreferredSize();
            component.setBounds(x, y - component.getBaseline(size.width, size.height), size.width, size.height);
            x += size.width + myOffset;
          }
        }
      };
    }

    private void createVersion(boolean update) {
      if (!update || !(myPlugin instanceof PluginNode)) {
        return;
      }

      String version = StringUtil.defaultIfEmpty(myPlugin.getVersion(), null);
      String date = getLastUpdatedDate(myPlugin);

      if (version != null || date != null) {
        myVersionPanel = createNameBaselinePanel();
        myNameButtons.add(myVersionPanel, BorderLayout.CENTER);

        if (version != null) {
          myVersion = new JLabel("Version " + version);
          myVersion.setOpaque(false);
          myVersionPanel.add(installTiny(myVersion));
        }

        if (date != null) {
          myLastUpdated = new JLabel(date, AllIcons.Plugins.Updated, SwingConstants.CENTER);
          myLastUpdated.setOpaque(false);
          myVersionPanel.add(installTiny(myLastUpdated));
        }
      }
    }

    public void updateErrors() {
      boolean errors = PluginManagerCore.isIncompatible(myPlugin) || myPluginsModel.hasProblematicDependencies(myPlugin.getPluginId());

      updateIcon(errors, true);

      if (errors) {
        if (myErrorPanel == null) {
          myErrorPanel = createNameBaselinePanel();

          JLabel errorMessage = new JLabel() {
            boolean myShowTooltip;

            @Override
            public Dimension getPreferredSize() {
              myShowTooltip = false;

              Dimension size = super.getPreferredSize();
              Container parent = getParent();
              if (parent != null && parent.getWidth() > 0) {
                int width = parent.getWidth();
                int offset = parent.getInsets().left;
                int components = parent.getComponentCount();

                width -= offset;

                if (components == 2) {
                  width -= offset;
                  width -= parent.getComponent(1).getPreferredSize().width;
                  width -= JBUI.scale(40);
                }

                if (size.width > width && width > 0) {
                  size.width = width;
                  myShowTooltip = true;
                }
              }
              return size;
            }

            @Override
            public String getToolTipText() {
              return myShowTooltip ? getText() : null;
            }
          };

          errorMessage.setForeground(JBColor.red);
          errorMessage.setOpaque(false);
          myErrorPanel.add(errorMessage);
          myMouseComponents.add(errorMessage);

          Ref<Boolean> enableAction = new Ref<>();
          errorMessage.setText(getErrorMessage(myPluginsModel, myPlugin, enableAction));

          if (!enableAction.isNull()) {
            LinkLabel errorAction = new LinkLabel("Enable", null);
            myErrorPanel.add(errorAction);
            myMouseComponents.add(errorAction);
          }
        }

        if (myVersionPanel != null && myVersionPanel.isVisible()) {
          myVersionPanel.setVisible(false);
          myNameButtons.remove(myVersionPanel);
        }
        myNameButtons.add(myErrorPanel);
        myErrorPanel.setVisible(true);
        myNameButtons.doLayout();
      }
      else {
        boolean layout = false;
        if (myErrorPanel != null && myErrorPanel.isVisible()) {
          myErrorPanel.setVisible(false);
          myNameButtons.remove(myErrorPanel);
          layout = true;
        }
        if (myVersionPanel != null && !myVersionPanel.isVisible()) {
          myNameButtons.add(myVersionPanel);
          myVersionPanel.setVisible(true);
          layout = true;
        }
        if (layout) {
          myNameButtons.doLayout();
        }
      }
    }

    @Nullable
    private static String getChangeNotes(@NotNull IdeaPluginDescriptor plugin) {
      String notes = plugin.getChangeNotes();
      return StringUtil.isEmptyOrSpaces(notes) ? null : "<b>Change Notes</b><br>\n" + notes;
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

      boolean enabled = myPluginsModel.isEnabled(myPlugin);

      myIconLabel.setEnabled(enabled);
      myName.setForeground(enabled ? null : DisabledColor);
      if (myDescription != null) {
        myDescription.setForeground(enabled ? grayedFg : DisabledColor);
      }

      if (myEnableDisableButton != null) {
        myEnableDisableButton.setBackground(background);
      }
      if (myEnableDisableUninstallButton != null) {
        myEnableDisableUninstallButton.setBackground(background);
      }
    }

    @Override
    public void addMouseListeners(@NotNull MouseAdapter listener) {
      super.addMouseListeners(listener);
      if (myEnableDisableUninstallButton != null) {
        for (JButton button : UIUtil.findComponentsOfType(myEnableDisableUninstallButton, JButton.class)) {
          addMouseListeners(button, listener);
        }
      }
    }

    @Override
    public void removeMouseListeners(@NotNull MouseAdapter listener) {
      super.removeMouseListeners(listener);
      if (myEnableDisableUninstallButton != null) {
        for (JButton button : UIUtil.findComponentsOfType(myEnableDisableUninstallButton, JButton.class)) {
          removeMouseListeners(button, listener);
        }
      }
    }

    @NotNull
    private String getEnabledTitle() {
      return myPluginsModel.getEnabledTitle(myPlugin);
    }

    public void updateAfterUninstall() {
      if (myEnableDisableUninstallButton == null) {
        return;
      }

      Container parent = myEnableDisableUninstallButton.getParent();

      parent.remove(myEnableDisableUninstallButton);
      myMouseComponents.remove(myEnableDisableUninstallButton);
      myEnableDisableUninstallButton = null;

      if (myUpdateButton != null) {
        parent.remove(myUpdateButton);
        myMouseComponents.remove(myUpdateButton);
        myUpdateButton = null;
      }
      if (myRestartButton == null) {
        myRestartButton = new RestartButton(myPluginsModel);
        parent.add(myRestartButton);
        myMouseComponents.add(myRestartButton);
      }

      doLayout();
    }

    public void updateEnabledState() {
      if (myEnableDisableButton != null) {
        myEnableDisableButton.setText(getEnabledTitle());
      }
      if (myEnableDisableUninstallButton != null) {
        myEnableDisableUninstallButton.setText(getEnabledTitle());
      }
      updateErrors();
      setSelection(mySelection);
    }
  }

  private static class GridCellPluginComponent extends CellPluginComponent {
    private JLabel myLastUpdated;
    private JLabel myDownloads;
    private JLabel myRating;
    private final JButton myInstallButton;

    public GridCellPluginComponent(@NotNull MyPluginModel pluginsModel,
                                   @NotNull IdeaPluginDescriptor plugin,
                                   @NotNull TagBuilder tagBuilder) {
      super(plugin);
      pluginsModel.addComponent(this);

      JPanel container = new NonOpaquePanel();
      add(container);

      JPanel centerPanel = createPanel(container, null, JBUI.scale(10), offset5(), JBUI.scale(180));

      addNameComponent(centerPanel, null);
      addTags(centerPanel, tagBuilder);
      addDescriptionComponent(centerPanel, getShortDescription(myPlugin, false), new LineFunction(3, true));
      if (myDescription != null) {
        UIUtil.setCursor(myDescription, Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
      }

      createMetricsPanel(centerPanel);

      myInstallButton = new InstallButton(false);
      add(myInstallButton);
      myMouseComponents.add(myInstallButton);

      setLayout(new AbstractLayoutManager() {
        @Override
        public Dimension preferredLayoutSize(Container parent) {
          Dimension size = container.getPreferredSize();
          size.height += offset5();
          size.height += myInstallButton.getPreferredSize().height;
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
          Dimension buttonSize = myInstallButton.getPreferredSize();
          Border border = myInstallButton.getBorder();
          int borderOffset = border == null ? 0 : border.getBorderInsets(myInstallButton).left;
          myInstallButton
            .setBounds(bounds.x + location.x - borderOffset, bounds.y + offset5() + bounds.height, buttonSize.width, buttonSize.height);
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

    private void addTags(@NotNull JPanel parent, @NotNull TagBuilder tagBuilder) {
      List<String> tags = getTags(myPlugin);
      if (tags.isEmpty()) {
        return;
      }

      NonOpaquePanel panel = new NonOpaquePanel(new HorizontalLayout(JBUI.scale(6)));
      parent.add(panel);

      for (String tag : tags) {
        TagComponent component = installTiny(tagBuilder.createTagComponent(tag));
        panel.add(component);
        myMouseComponents.add(component);
      }
    }

    @Override
    public void setLinkListener(@NotNull LinkListener<IdeaPluginDescriptor> listener) {
      super.setLinkListener(listener);

      MouseListener mouseListener = new MouseAdapter() {
        @Override
        public void mouseEntered(MouseEvent event) {
          myName.entered(event);
        }

        @Override
        public void mouseExited(MouseEvent event) {
          myName.exited(event);
        }
      };
      myIconLabel.addMouseListener(mouseListener);

      if (myDescription != null) {
        myDescription.addMouseListener(new MouseAdapter() {
          @Override
          public void mouseClicked(MouseEvent event) {
            if (SwingUtilities.isLeftMouseButton(event)) {
              listener.linkSelected(myName, myPlugin);
            }
          }
        });
        myDescription.addMouseListener(mouseListener);
      }
    }

    @Override
    public void addMouseListeners(@NotNull MouseAdapter listener) {
    }

    @Override
    public void removeMouseListeners(@NotNull MouseAdapter listener) {
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

    public void updateAfterUninstall() {
      // XXX
    }
  }

  private static int offset5() {
    return JBUI.scale(5);
  }

  private enum SelectionType {
    SELECTION, HOVER, NONE
  }

  private class DetailsPageListComponent extends OpaquePanel {
    private final IdeaPluginDescriptor myPlugin;
    private JLabel myNameComponent;
    private JLabel myIconLabel;
    private JButton myUpdateButton;
    private JButton myInstallButton;
    private JButton myEnableDisableButton;
    private RestartButton myRestartButton;
    private JBOptionButton myEnableDisableUninstallButton;

    public DetailsPageListComponent(@NotNull IdeaPluginDescriptor plugin, boolean update) {
      super(new BorderLayout(0, JBUI.scale(32)), mySearchTextField.getTextEditor().getBackground());
      myPlugin = plugin;

      setBorder(JBUI.Borders.empty(15, 20, 0, 0));

      JPanel header = createHeaderPanel();
      JPanel centerPanel = createCenterPanel(update);
      header.add(centerPanel);

      createTagPanel(centerPanel);
      createMetricsPanel(centerPanel);
      createErrorPanel(centerPanel);
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
      nameButtons.add(createButtons(update), BorderLayout.EAST);
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

      if (myPlugin instanceof IdeaPluginDescriptorImpl && ((IdeaPluginDescriptorImpl)myPlugin).isDeleted()) {
        buttons.add(myRestartButton = new RestartButton(myPluginsModel));
      }
      else {
        if (update) {
          buttons.add(myUpdateButton = new UpdateButton());
        }
        if (myPlugin instanceof PluginNode) {
          buttons.add(myInstallButton = new InstallButton(true));
        }
        else if (myPlugin.isBundled()) {
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

      for (Component component : UIUtil.uiChildren(buttons)) {
        component.setBackground(MAIN_BG_COLOR);
      }

      return buttons;
    }

    @NotNull
    private JPanel createHeaderPanel() {
      JPanel header = new NonOpaquePanel(new BorderLayout(JBUI.scale(20), 0));
      header.setBorder(JBUI.Borders.emptyRight(20));
      add(header, BorderLayout.NORTH);

      boolean jb = PluginManagerMain.isDevelopedByJetBrains(myPlugin);
      boolean errors = PluginManagerCore.isIncompatible(myPlugin) || myPluginsModel.hasProblematicDependencies(myPlugin.getPluginId());

      myIconLabel = new JLabel(PluginLogoInfo.getIcon(true, jb, errors, false));
      myIconLabel.setDisabledIcon(PluginLogoInfo.getIcon(true, jb, errors, true));
      myIconLabel.setVerticalAlignment(SwingConstants.TOP);
      myIconLabel.setOpaque(false);
      myIconLabel.setEnabled(myPlugin instanceof PluginNode || myPluginsModel.isEnabled(myPlugin));
      header.add(myIconLabel, BorderLayout.WEST);

      return header;
    }

    private void createTagPanel(@NotNull JPanel centerPanel) {
      java.util.List<String> tags = getTags(myPlugin);

      if (!tags.isEmpty()) {
        NonOpaquePanel tagPanel = new NonOpaquePanel(new HorizontalLayout(JBUI.scale(6)));
        centerPanel.add(tagPanel);

        for (String tag : tags) {
          tagPanel.add(myTagBuilder.createTagComponent(tag));
        }
      }
    }

    private void createMetricsPanel(@NotNull JPanel centerPanel) {
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
        centerPanel.add(metrics);

        if (date != null) {
          JLabel lastUpdated = new JLabel(date, AllIcons.Plugins.Updated, SwingConstants.CENTER);
          lastUpdated.setOpaque(false);
          lastUpdated.setForeground(grayedFg);
          metrics.add(lastUpdated);
        }

        if (downloads != null) {
          JLabel downloadsComponent = new JLabel(downloads, AllIcons.Plugins.Downloads, SwingConstants.CENTER);
          downloadsComponent.setOpaque(false);
          downloadsComponent.setForeground(grayedFg);
          metrics.add(downloadsComponent);
        }

        if (rating != null) {
          RatesPanel ratesPanel = new RatesPanel();
          ratesPanel.setRate(rating);
          metrics.add(ratesPanel);
        }
      }
    }

    private void createErrorPanel(@NotNull JPanel centerPanel) {
      if (PluginManagerCore.isIncompatible(myPlugin) || myPluginsModel.hasProblematicDependencies(myPlugin.getPluginId())) {
        JPanel errorPanel = new NonOpaquePanel(new HorizontalLayout(JBUI.scale(8)));
        centerPanel.add(errorPanel);

        JLabel errorMessage = new JLabel();
        errorMessage.setForeground(JBColor.red);
        errorMessage.setOpaque(false);
        errorPanel.add(errorMessage);

        Ref<Boolean> enableAction = new Ref<>();
        errorMessage.setText(getErrorMessage(myPluginsModel, myPlugin, enableAction));

        if (!enableAction.isNull()) {
          LinkLabel errorAction = new LinkLabel("Enable", null);
          errorPanel.add(errorAction);
        }
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
          bottomPanel.add(descriptionComponent, VerticalLayout.FILL_HORIZONTAL);

          if (descriptionComponent.getCaret() != null) {
            descriptionComponent.setCaretPosition(0);
          }
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
            for (JLabel label : labels) {
              width = Math.max(width, label.getPreferredSize().width);
            }
            for (JLabel label : labels) {
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
      myPluginsModel.doUninstall(this, myPlugin, () -> {
        Container parent = myEnableDisableUninstallButton.getParent();

        parent.remove(myEnableDisableUninstallButton);
        myEnableDisableUninstallButton = null;

        if (myUpdateButton != null) {
          parent.remove(myUpdateButton);
          myUpdateButton = null;
        }
        if (myInstallButton != null) {
          parent.remove(myInstallButton);
          myInstallButton = null;
        }
        if (myRestartButton == null) {
          parent.add(myRestartButton = new RestartButton(myPluginsModel));
        }

        doLayout();
      });
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
      setBorder(JBUI.Borders.empty(0, 8));
    }

    @Override
    protected void paintComponent(Graphics g) {
      //noinspection UseJBColor
      g.setColor(myUnderline ? new Color(myColor.getRed(), myColor.getGreen(), myColor.getBlue(), 100) : myColor);
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
        component.setBounds(insets.left, y, myFillComponents.contains(component) ? width : Math.min(width, size.width), size.height);
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

  private static class MyPluginModel extends InstalledPluginsTableModel {
    private final List<ListPluginComponent> myListComponents = new ArrayList<>();
    private final Map<String, ListPluginComponent> myListMap = new HashMap<>();
    private final Map<String, GridCellPluginComponent> myGridMap = new HashMap<>();
    private PluginsGroup myEnabledGroup;

    public boolean needRestart;
    public boolean createShutdownCallback = true;

    public void addComponent(@NotNull CellPluginComponent component) {
      if (component instanceof ListPluginComponent) {
        myListComponents.add((ListPluginComponent)component);
        myListMap.put(component.myPlugin.getPluginId().getIdString(), (ListPluginComponent)component);
      }
      else {
        myGridMap.put(component.myPlugin.getPluginId().getIdString(), (GridCellPluginComponent)component);
      }
    }

    public void setEnabledGroup(@NotNull PluginsGroup group) {
      myEnabledGroup = group;
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

      for (ListPluginComponent component : myListComponents) {
        component.updateEnabledState();
      }

      int enabled = 0;
      for (IdeaPluginDescriptor descriptor : myEnabledGroup.descriptors) {
        if (isEnabled(descriptor)) {
          enabled++;
        }
      }
      myEnabledGroup.titleWithCount("Bundled", enabled);
      myEnabledGroup.titleLabel.setText(myEnabledGroup.title);
    }

    public void doUninstall(@NotNull Component uiParent, @NotNull IdeaPluginDescriptor plugin, @Nullable Runnable update) {
      if (!dependent((IdeaPluginDescriptorImpl)plugin).isEmpty()) {
        String message = IdeBundle.message("several.plugins.depend.on.0.continue.to.remove", plugin.getName());
        String title = IdeBundle.message("title.plugin.uninstall");
        if (Messages.showYesNoDialog(uiParent, message, title, Messages.getQuestionIcon()) != Messages.YES) {
          return;
        }
      }

      try {
        ((IdeaPluginDescriptorImpl)plugin).setDeleted(true);
        PluginInstaller.prepareToUninstall(plugin.getPluginId());
        needRestart |= plugin.isEnabled();
      }
      catch (IOException e) {
        PluginManagerMain.LOG.error(e);
      }

      if (update != null) {
        update.run();
      }

      String id = plugin.getPluginId().getIdString();

      ListPluginComponent listComponent = myListMap.get(id);
      if (listComponent != null) {
        listComponent.updateAfterUninstall();
      }

      GridCellPluginComponent gridComponent = myGridMap.get(id);
      if (gridComponent != null) {
        gridComponent.updateAfterUninstall();
      }

      for (ListPluginComponent component : myListComponents) {
        component.updateErrors();
      }
    }
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
        for (String sep : new String[]{". ", ".\n", ": ", ":\n"}) {
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
    private static final Icon ModifierInvalid = IconLoader.getIcon("/plugins/modifierInvalid.svg", AllIcons.class); // 15x15
    private static final Icon ModifierJBLogo = IconLoader.getIcon("/plugins/modifierJBLogo.svg", AllIcons.class); // 14x14

    private static final LayeredIcon PluginLogoJB_40 = new LayeredIcon(2);
    private static final LayeredIcon PluginLogoError_40 = new LayeredIcon(2);
    private static final LayeredIcon PluginLogoJBError_40 = new LayeredIcon(3);

    private static final LayeredIcon PluginLogoDisabledJB_40 = new LayeredIcon(2);
    private static final LayeredIcon PluginLogoDisabledError_40 = new LayeredIcon(2);
    private static final LayeredIcon PluginLogoDisabledJBError_40 = new LayeredIcon(3);

    private static final LayeredIcon PluginLogoJB_80 = new LayeredIcon(2);
    private static final LayeredIcon PluginLogoError_80 = new LayeredIcon(2);
    private static final LayeredIcon PluginLogoJBError_80 = new LayeredIcon(3);

    private static final LayeredIcon PluginLogoDisabledJB_80 = new LayeredIcon(2);
    private static final LayeredIcon PluginLogoDisabledError_80 = new LayeredIcon(2);
    private static final LayeredIcon PluginLogoDisabledJBError_80 = new LayeredIcon(3);

    static {
      setSouthEast(PluginLogoJB_40, AllIcons.Plugins.PluginLogo_40, ModifierJBLogo);
      setSouthWest(PluginLogoError_40, AllIcons.Plugins.PluginLogo_40, ModifierInvalid);
      setSouthEastWest(PluginLogoJBError_40, AllIcons.Plugins.PluginLogo_40, ModifierJBLogo, ModifierInvalid);

      Icon disabledJBLogo = IconLoader.getDisabledIcon(ModifierJBLogo);
      assert disabledJBLogo != null;

      setSouthEast(PluginLogoDisabledJB_40, AllIcons.Plugins.PluginLogoDisabled_40, disabledJBLogo);
      setSouthWest(PluginLogoDisabledError_40, AllIcons.Plugins.PluginLogoDisabled_40, ModifierInvalid);
      setSouthEastWest(PluginLogoDisabledJBError_40, AllIcons.Plugins.PluginLogoDisabled_40, disabledJBLogo, ModifierInvalid);

      Icon jbLogo2x = IconUtil.scale(ModifierJBLogo, 2);
      Icon errorLogo2x = IconUtil.scale(ModifierInvalid, 2);

      setSouthEast(PluginLogoJB_80, AllIcons.Plugins.PluginLogo_80, jbLogo2x);
      setSouthWest(PluginLogoError_80, AllIcons.Plugins.PluginLogo_80, errorLogo2x);
      setSouthEastWest(PluginLogoJBError_80, AllIcons.Plugins.PluginLogo_80, jbLogo2x, errorLogo2x);

      Icon disabledJBLogo2x = IconLoader.getDisabledIcon(jbLogo2x);
      assert disabledJBLogo2x != null;

      setSouthEast(PluginLogoDisabledJB_80, AllIcons.Plugins.PluginLogoDisabled_80, disabledJBLogo2x);
      setSouthWest(PluginLogoDisabledError_80, AllIcons.Plugins.PluginLogoDisabled_80, errorLogo2x);
      setSouthEastWest(PluginLogoDisabledJBError_80, AllIcons.Plugins.PluginLogoDisabled_80, disabledJBLogo2x, errorLogo2x);
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