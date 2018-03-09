// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins;

import com.intellij.icons.AllIcons;
import com.intellij.ide.HelpTooltip;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.ui.laf.darcula.ui.DarculaButtonUI;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.options.*;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.*;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.components.JBOptionButton;
import com.intellij.ui.components.JBPanelWithEmptyText;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextField;
import com.intellij.ui.components.labels.LinkLabel;
import com.intellij.ui.components.labels.LinkListener;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.ui.components.panels.OpaquePanel;
import com.intellij.util.Function;
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
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;
import java.util.regex.Pattern;

import static java.lang.System.out;

/**
 * @author Alexander Lobas
 */
public class PluginManagerConfigurableNew extends BaseConfigurable
  implements SearchableConfigurable, Configurable.NoScroll, Configurable.NoMargin, Configurable.TopComponentProvider,
             OptionalConfigurable {
  public static final String ID = "preferences.pluginManager2";
  public static final String DISPLAY_NAME = "Plugins (New Design)"; //IdeBundle.message("title.plugins");

  private static final String SELECTION_TAB_KEY = "PluginConfigurable.selectionTab";

  private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("MMM dd, yyyy");
  private static final DecimalFormat K_FORMAT = new DecimalFormat("###.#K");
  private static final DecimalFormat M_FORMAT = new DecimalFormat("###.#M");

  private static final String LONG_LONG_DESCRIPTION =
    "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris nisi ut aliquip ex ea commodo consequat.";

  private final TagBuilder myTagBuilder;

  private boolean myShowDetailPanel;
  private LinkListener<Pair<IdeaPluginDescriptor, Integer>> myNameListener;

  private CardLayoutPanel<Object, Object, JComponent> myCardPanel;
  private TabHeaderComponent myTabHeaderComponent;
  private TopComponentController myTopController;
  private final SearchTextField mySearchTextField;

  public PluginManagerConfigurableNew() {
    myTagBuilder = new TagBuilder() {
      @NotNull
      @Override
      public TagComponent createTagComponent(@NotNull String tag) {
        Color color;
        if ("graphics".equals(tag)) {
          color = new JBColor(0xEFE4CE, 0x5E584B);
        }
        else if ("misc".equals(tag)) {
          color = new JBColor(0xD8EDF8, 0x49606E);
        }
        else if ("EAP".equals(tag)) {
          color = new JBColor(0xF2D2CF, 0xF2D2CF);
        }
        else {
          color = new JBColor(0xEAEAEC, 0x4D4D4D);
        }

        String tooltip = "The EAP version does not guarantee the stability\nand availability of the plugin.";

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
  }

  @NotNull
  @Override
  public String getId() {
    return ID;
  }

  @Nls
  @Override
  public String getDisplayName() {
    return DISPLAY_NAME;
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return mySearchTextField.getTextEditor();
  }

  @Nullable
  @Override
  public JComponent createComponent() {
    JPanel panel = new JPanel(new BorderLayout());

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
    editor.setOpaque(true);
    editor.setBorder(JBUI.Borders.empty(0, 25));
    mySearchTextField.setBorder(JBUI.Borders.customLine(new JBColor(0xC5C5C5, 0x515151)));
    panel.add(mySearchTextField, BorderLayout.NORTH);

    myNameListener = (aSource, data) -> {
      myShowDetailPanel = true;
      JButton backButton = new JButton("Plugins", AllIcons.Actions.Back);
      backButton.addActionListener(event -> {
        removeDetailsPanel();
        myCardPanel.select(data.second, true);
        storeSelectionTab(data.second);
        myTabHeaderComponent.setSelection(data.second);
      });
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
        return createDetailsPanel(data.first, data.second == 2);
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
  public void apply() throws ConfigurationException {
    // TODO: Auto-generated method stub
  }

  @NotNull
  @Override
  public Component getCenterComponent(@NotNull TopComponentController controller) {
    myTopController = controller;
    return myTabHeaderComponent;
  }

  @NotNull
  private JComponent createTrendingPanel(@NotNull LinkListener<Pair<IdeaPluginDescriptor, Integer>> listener) {
    PluginsGroupComponent panel = new PluginsGroupComponent(new PluginsGridLayout(myTagBuilder), (aSource, aLinkData) -> listener
      .linkSelected(aSource, new Pair<>(aLinkData, 0)), descriptor -> new GridCellPluginComponent(descriptor, myTagBuilder));
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
      .linkSelected(aSource, new Pair<>(aLinkData, 1)), descriptor -> new ListPluginComponent(descriptor, false));

    PluginsGroup downloaded = new PluginsGroup();
    PluginsGroup bundled = new PluginsGroup();

    downloaded.descriptors.addAll(InstalledPluginsState.getInstance().getInstalledPlugins());

    ApplicationInfoEx appInfo = ApplicationInfoEx.getInstanceEx();

    for (IdeaPluginDescriptor descriptor : PluginManagerCore.getPlugins()) {
      if (!appInfo.isEssentialPlugin(descriptor.getPluginId().getIdString())) {
        if (descriptor.isBundled()) {
          bundled.descriptors.add(descriptor);
        }
        else {
          downloaded.descriptors.add(descriptor);
        }
      }
    }

    if (!downloaded.descriptors.isEmpty()) {
      downloaded.sortByName();
      downloaded.titleWithCount("Downloaded");
      panel.addGroup(downloaded);
    }

    bundled.sortByName();
    bundled.titleWithCount("Bundled");
    panel.addGroup(bundled);

    return createScrollPane(panel);
  }

  @NotNull
  private JComponent createUpdatesPanel(@NotNull LinkListener<Pair<IdeaPluginDescriptor, Integer>> listener) {
    PluginsGroupComponent panel = new PluginsGroupComponent(new PluginsListLayout(), (aSource, aLinkData) -> listener
      .linkSelected(aSource, new Pair<>(aLinkData, 2)), descriptor -> new ListPluginComponent(descriptor, true));
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

  @NotNull
  private JComponent createDetailsPanel(@NotNull IdeaPluginDescriptor plugin, boolean update) {
    JPanel panel = new OpaquePanel(new BorderLayout(0, offset5()), mySearchTextField.getTextEditor().getBackground());
    panel.setBorder(JBUI.Borders.empty(15, 20));

    JPanel header = new NonOpaquePanel(new BorderLayout(offset5(), 0));

    JLabel myIconLabel = new JLabel(AllIcons.Plugins.PluginLogo_80);
    myIconLabel.setDisabledIcon(AllIcons.Plugins.PluginLogoDisabled_80);
    myIconLabel.setVerticalAlignment(SwingConstants.TOP);
    myIconLabel.setOpaque(false);
    myIconLabel.setEnabled(plugin.isEnabled());
    header.add(myIconLabel, BorderLayout.WEST);

    JPanel centerPanel = new NonOpaquePanel(new VerticalLayout(offset5()));
    header.add(centerPanel);

    boolean bundled = plugin.isBundled();

    JPanel buttons = new NonOpaquePanel(new HorizontalLayout(offset5()));
    if (update) {
      buttons.add(new UpdateButton());
    }
    if (plugin instanceof PluginNode) {
      buttons.add(new InstallButton(true));
    }
    else if (bundled) {
      buttons.add(new JButton(plugin.isEnabled() ? "Disable" : "Enable"));
    }
    else {
      AbstractAction enableDisableAction = new AbstractAction(plugin.isEnabled() ? "Disable" : "Enable") {
        @Override
        public void actionPerformed(ActionEvent e) {
          out.println("d");
          // TODO: Auto-generated method stub
        }
      };
      AbstractAction uninstallAction = new AbstractAction("Uninstall") {
        @Override
        public void actionPerformed(ActionEvent e) {
          // TODO: Auto-generated method stub
        }
      };
      buttons.add(new MyOptionButton(enableDisableAction, uninstallAction));
    }

    Color background = UIUtil.getListBackground();
    for (Component component : UIUtil.uiChildren(buttons)) {
      component.setBackground(background);
    }

    JPanel nameButtons = new NonOpaquePanel(new BorderLayout(offset5(), 0));
    JLabel nameComponent = new JLabel(plugin.getName());
    Font font = nameComponent.getFont();
    if (font != null) {
      nameComponent.setFont(font.deriveFont(Font.BOLD, 30));
    }
    if (!plugin.isEnabled()) {
      nameComponent.setForeground(DarculaButtonUI.getDisabledTextColor());
    }
    nameButtons.add(nameComponent, BorderLayout.WEST);
    nameButtons.add(buttons, BorderLayout.EAST);
    centerPanel.add(nameButtons, VerticalLayout.FILL_HORIZONTAL);

    Color grayedFg = new JBColor(Gray._130, Gray._120);

    String version = bundled ? "bundled" : plugin.getVersion();
    if (!StringUtil.isEmptyOrSpaces(version)) {
      if (!bundled) {
        version = "v" + version;
      }
      JLabel versionComponent = new JLabel(version);
      versionComponent.setOpaque(false);
      versionComponent.setForeground(grayedFg);
      nameButtons.add(versionComponent);

      int nameBaseline = nameComponent.getBaseline(nameComponent.getWidth(), nameComponent.getHeight());
      int versionBaseline = versionComponent.getBaseline(versionComponent.getWidth(), versionComponent.getHeight());
      versionComponent.setBorder(JBUI.Borders.emptyTop(nameBaseline - versionBaseline + JBUI.scale(6)));
    }

    List<String> tags = getTags(plugin);
    if (!tags.isEmpty()) {
      NonOpaquePanel tagPanel = new NonOpaquePanel(new HorizontalLayout(JBUI.scale(offset5())));
      centerPanel.add(tagPanel);

      for (String tag : tags) {
        tagPanel.add(myTagBuilder.createTagComponent(tag));
      }
    }

    if (plugin instanceof PluginNode) {
      String downloads = getDownloads(plugin);
      String date = getLastUpdatedDate(plugin);
      String rating = getRating(plugin);

      if (downloads != null || date != null || rating != null) {
        JPanel metrics = new NonOpaquePanel(new HorizontalLayout(JBUI.scale(20)));
        metrics.setBorder(JBUI.Borders.emptyTop(5));
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

    panel.add(header, BorderLayout.NORTH);

    String description = plugin.getDescription();
    String vendor = bundled ? null : plugin.getVendor();
    String size = plugin instanceof PluginNode ? ((PluginNode)plugin).getSize() : null;

    if (!StringUtil.isEmptyOrSpaces(description) || !StringUtil.isEmptyOrSpaces(vendor) || !StringUtil.isEmptyOrSpaces(size)) {
      JPanel bottomPanel = new OpaquePanel(new VerticalLayout(offset5()), background);
      JBScrollPane scrollPane = new JBScrollPane(bottomPanel);
      scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
      scrollPane.setBorder(null);
      panel.add(scrollPane);

      if (!StringUtil.isEmptyOrSpaces(description)) {
        JEditorPane descriptionComponent = new JEditorPane();
        descriptionComponent.setEditorKit(UIUtil.getHTMLEditorKit());
        descriptionComponent.setEditable(false);
        descriptionComponent.setOpaque(false);
        descriptionComponent.setBorder(null);
        descriptionComponent.setText(XmlStringUtil.wrapInHtml(description));
        bottomPanel.add(descriptionComponent, VerticalLayout.FILL_HORIZONTAL);

        if (descriptionComponent.getCaret() != null) {
          descriptionComponent.setCaretPosition(0);
        }
      }

      if (!StringUtil.isEmptyOrSpaces(vendor) || !StringUtil.isEmptyOrSpaces(size)) {
        List<JLabel> labels = new ArrayList<>();

        if (!StringUtil.isEmptyOrSpaces(vendor)) {
          JPanel linePanel = createLabelsPanel(bottomPanel, labels, "Vendor:", vendor, plugin.getVendorUrl());
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

    return panel;
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
    String downloads = plugin.getDownloads();
    if (!StringUtil.isEmptyOrSpaces(downloads)) {
      try {
        Long value = Long.valueOf(downloads);
        if (value > 1000) {
          return value < 1000000 ? K_FORMAT.format(value / 1000D) : M_FORMAT.format(value / 1000000D);
        }
      }
      catch (NumberFormatException ignore) { }
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
      catch (NumberFormatException ignore) { }
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

    public TabHeaderComponent(@NotNull DefaultActionGroup actions, @NotNull TabHeaderListener listener) {
      myListener = listener;
      add(myToolbarComponent = createToolbar(actions));
      setBackground(JBUI.CurrentTheme.ToolWindow.headerActiveBackground());
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
      int x = (getWidth() - mySizeInfo.width) / 2;
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
      int x = (getWidth() - mySizeInfo.width) / 2;
      int height = getHeight();
      int tabTitleY = fm.getAscent() + (height - fm.getHeight()) / 2;

      for (int i = 0, size = myTabs.size(); i < size; i++) {
        if (mySelectionTab == i || myHoverTab == i) {
          Rectangle bounds = mySizeInfo.tabs[i];
          g.setColor(mySelectionTab == i
                     ? JBUI.CurrentTheme.ToolWindow.tabSelectedActiveBackground()
                     : JBUI.CurrentTheme.ToolWindow.tabHoveredActiveBackground());
          g.fillRect(x + bounds.x, 0, bounds.width, height);
          g.setColor(getForeground());
        }

        g.drawString(myTabs.get(i).compute(), x + mySizeInfo.tabTitleX[i], tabTitleY);
      }
    }

    @Override
    public void setBounds(int x, int y, int width, int height) {
      height = getParent().getHeight();
      super.setBounds(x, 0, width, height);

      calculateSize();

      int startX = (width - mySizeInfo.width) / 2;
      Dimension size = myToolbarComponent.getPreferredSize();
      myToolbarComponent.setBounds(startX + mySizeInfo.toolbarX, (height - size.height) / 2, size.width, size.height);
    }

    @Override
    public Dimension getPreferredSize() {
      calculateSize();
      return new Dimension(mySizeInfo.width, JBUI.scale(35));
    }

    private void calculateSize() {
      if (mySizeInfo != null) {
        return;
      }

      mySizeInfo = new SizeInfo();

      int size = myTabs.size();
      mySizeInfo.tabs = new Rectangle[size];
      mySizeInfo.tabTitleX = new int[size];

      int offset = JBUI.scale(24);
      int x = 0;
      FontMetrics fm = getFontMetrics(getFont());

      for (int i = 0; i < size; i++) {
        int tabWidth = offset + SwingUtilities2.stringWidth(null, fm, myTabs.get(i).compute()) + offset;
        mySizeInfo.tabTitleX[i] = x + offset;
        mySizeInfo.tabs[i] = new Rectangle(x, 0, tabWidth, -1);
        x += tabWidth;
      }

      Dimension toolbarSize = myToolbarComponent.getPreferredSize();
      mySizeInfo.width = x + offset + toolbarSize.width + offset;
      mySizeInfo.toolbarX = x + offset;
    }
  }

  private static class SizeInfo {
    public int width;

    public Rectangle[] tabs;
    public int[] tabTitleX;

    public int toolbarX;
  }

  private class PluginsGroupComponent extends JBPanelWithEmptyText {
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
      setBackground(mySearchTextField.getTextEditor().getBackground());

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
      panel.setBorder(JBUI.Borders.empty(4, 13, 5, 14));
      panel.add(new JLabel(group.title), BorderLayout.WEST);

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
        //noinspection unchecked
        pluginComponent.myName.setListener(myListener, descriptor);
      }
    }
  }

  private static class PluginsListLayout extends AbstractLayoutManager {
    private int myLineHeight;

    @Override
    public Dimension preferredLayoutSize(Container parent) {
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
    public void layoutContainer(Container parent) {
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
          description.putClientProperty("parent.width", new Integer(parentWidth));

          plugin.doLayout();
          myLineHeight = Math.max(myLineHeight, plugin.getPreferredSize().height);
        }
      }
    }
  }

  private static class PluginsGridLayout extends AbstractLayoutManager {
    private final Dimension myCellSize;

    @SuppressWarnings("AssignmentToStaticFieldFromInstanceMethod")
    public PluginsGridLayout(@NotNull TagBuilder tagBuilder) {
      PluginNode pluginNode = new PluginNode(null, "Language Language Lang", null);
      pluginNode.setDescription(
        "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris nisi ut aliquip ex ea commodo consequat.");
      pluginNode.setCategory("languages");
      pluginNode.setDate(String.valueOf(System.currentTimeMillis()));
      pluginNode.setDownloads("123456");
      pluginNode.setRating("5.5");
      GridCellPluginComponent component = new GridCellPluginComponent(pluginNode, tagBuilder);
      component.doLayout();

      myCellSize = component.getPreferredSize();
      myCellSize.height += offset5();
    }

    @Override
    public Dimension preferredLayoutSize(Container parent) {
      int width = getParentWidth(parent);
      int cellWidth = myCellSize.width;
      int columns = width / cellWidth;

      if (columns < 2) {
        columns = 2;
      }
      width = columns * cellWidth;

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

        height += rows * cellHeight;
      }

      return new Dimension(width, height);
    }

    @Override
    public void layoutContainer(Container parent) {
      List<UIPluginGroup> groups = ((PluginsGroupComponent)parent).myGroups;
      int width = parent.getWidth();
      int y = 0;
      int columns = Math.max(1, width / myCellSize.width);

      for (UIPluginGroup group : groups) {
        Component component = group.panel;
        int height = component.getPreferredSize().height;
        component.setBounds(0, y, width, height);
        y += height;
        y += layoutPlugins(group.plugins, y, columns);
      }
    }

    private int layoutPlugins(List<CellPluginComponent> plugins, int startY, int columns) {
      int x = 0;
      int y = 0;
      int width = myCellSize.width;
      int height = myCellSize.height;
      int column = 0;

      for (int i = 0, size = plugins.size(); i < size; i++) {
        plugins.get(i).setBounds(x, startY + y, width, height);
        x += width;

        if (++column == columns || i == size - 1) {
          x = 0;
          y += height;
          column = 0;
        }
      }

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
    public LinkLabel rightAction;
    public java.util.List<IdeaPluginDescriptor> descriptors = new ArrayList<>();

    public void titleWithCount(@NotNull String text) {
      title = text + " (" + descriptors.size() + ")";
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

    protected JLabel myIconLabel;
    protected LinkLabel myName;
    protected JEditorPane myDescription;

    protected SelectionType mySelection = SelectionType.NONE;

    protected CellPluginComponent(@NotNull IdeaPluginDescriptor plugin) {
      myPlugin = plugin;
    }

    @NotNull
    protected JPanel createPanel() {
      return createPanel(this, this, 0);
    }

    @NotNull
    protected JPanel createPanel(@NotNull JPanel parent, @NotNull JPanel root, int width) {
      parent.setLayout(new BorderLayout(offset5(), 0));

      myIconLabel = new JLabel(AllIcons.Plugins.PluginLogo_40);
      myIconLabel.setVerticalAlignment(SwingConstants.TOP);
      myIconLabel.setOpaque(false);
      parent.add(myIconLabel, BorderLayout.WEST);

      JPanel centerPanel = new NonOpaquePanel(new VerticalLayout(offset5(), width));
      parent.add(centerPanel);

      root.setOpaque(true);

      return centerPanel;
    }

    protected void addNameComponent(@NotNull JPanel parent, @Nullable Object constraints) {
      myName = new LinkComponent();
      myName.setText(myPlugin.getName());
      parent.add(RelativeFont.BOLD.install(myName), constraints);
    }

    protected void addDescriptionComponent(@NotNull JPanel parent,
                                           @Nullable String description,
                                           @Nullable Function<JEditorPane, Integer> heightFunction) {
      if (StringUtil.isEmptyOrSpaces(description)) {
        return;
      }

      myDescription = new JEditorPane() {
        @Override
        public Dimension getPreferredSize() {
          Integer property = (Integer)getClientProperty("parent.width");
          int width = property == null ? JBUI.scale(180) : property;
          View view = getUI().getRootView(this);
          view.setSize(width, Integer.MAX_VALUE);
          int height = heightFunction == null ? (int)(view.getPreferredSpan(View.Y_AXIS) + JBUI.scale(2f)) : heightFunction.fun(this);
          return new Dimension(width, height);
        }
      };
      myDescription.setEditorKit(UIUtil.getHTMLEditorKit());
      myDescription.setEditorKit(new UIUtil.JBWordWrapHtmlEditorKit());
      myDescription.setEditable(false);
      myDescription.setOpaque(false);
      myDescription.setBorder(null);
      myDescription.setText(XmlStringUtil.wrapInHtml(description));
      parent.add(installTiny(myDescription));

      if (myDescription.getCaret() != null) {
        myDescription.setCaretPosition(0);
      }
    }

    @NotNull
    public SelectionType getSelection() {
      return mySelection;
    }

    private static final Color HOVER_COLOR = new JBColor(0xE9EEF5, 0xE9EEF5);
    private static final Color GRAY_COLOR = new JBColor(Gray._130, Gray._120);

    public void setSelection(@NotNull SelectionType type) {
      mySelection = type;

      updateColors(GRAY_COLOR, type == SelectionType.NONE ? UIUtil.getListBackground() : HOVER_COLOR);
      repaint();
    }

    protected void updateColors(@NotNull Color grayedFg, @NotNull Color background) {
      setBackground(background);

      if (myDescription != null) {
        myDescription.setForeground(grayedFg);
      }
    }

    public void addMouseListeners(@NotNull MouseAdapter listener) {
      addMouseListener(listener);
      addMouseMotionListener(listener);
      myName.addMouseListener(listener);
      myName.addMouseMotionListener(listener);
      if (myDescription != null) {
        myDescription.addMouseListener(listener);
        myDescription.addMouseMotionListener(listener);
      }
    }

    public void removeMouseListeners(@NotNull MouseAdapter listener) {
      removeMouseListener(listener);
      removeMouseMotionListener(listener);
      myName.removeMouseListener(listener);
      myName.removeMouseMotionListener(listener);
      if (myDescription != null) {
        myDescription.removeMouseListener(listener);
        myDescription.removeMouseMotionListener(listener);
      }
    }

    @NotNull
    public static CellPluginComponent get(@NotNull MouseEvent event) {
      //noinspection ConstantConditions
      return UIUtil.getParentOfType(CellPluginComponent.class, event.getComponent());
    }
  }

  private static class ListPluginComponent extends CellPluginComponent {
    private JLabel myVersion;
    private JLabel myLastUpdated;
    private JButton myUpdateButton;
    private JButton myEnableDisableButton;
    private JBOptionButton myEnableDisableUninstallButton;

    public ListPluginComponent(@NotNull IdeaPluginDescriptor plugin, boolean update) {
      super(plugin);

      JPanel buttons = new NonOpaquePanel(new HorizontalLayout(offset5()));
      if (update) {
        myUpdateButton = new UpdateButton();
        buttons.add(myUpdateButton);
      }
      if (plugin.isBundled()) {
        myEnableDisableButton = new JButton(plugin.isEnabled() ? "Disable" : "Enable");
        buttons.add(myEnableDisableButton);
      }
      else {
        AbstractAction enableDisableAction = new AbstractAction(plugin.isEnabled() ? "Disable" : "Enable") {
          @Override
          public void actionPerformed(ActionEvent e) {
            // TODO: Auto-generated method stub
          }
        };
        AbstractAction uninstallAction = new AbstractAction("Uninstall") {
          @Override
          public void actionPerformed(ActionEvent e) {
            // TODO: Auto-generated method stub
          }
        };
        myEnableDisableUninstallButton = new MyOptionButton(enableDisableAction, uninstallAction);
        buttons.add(myEnableDisableUninstallButton);
      }

      JPanel centerPanel = createPanel();
      setBorder(JBUI.Borders.empty(10));

      JPanel nameButtons = new NonOpaquePanel(new BorderLayout());
      nameButtons.add(buttons, BorderLayout.EAST);
      centerPanel.add(nameButtons, VerticalLayout.FILL_HORIZONTAL);

      myIconLabel.setDisabledIcon(AllIcons.Plugins.PluginLogoDisabled_40);

      addNameComponent(nameButtons, BorderLayout.WEST);

      if (update) {
        addDescriptionComponent(centerPanel, getChangeNotes(plugin), pane -> {
          try {
            int line = 0;
            int startLineY = -1;
            int length = myDescription.getDocument().getLength();

            for (int i = 0; i < length; i++) {
              Rectangle r = myDescription.modelToView(i);
              if (r != null && r.height > 0 && startLineY < r.y) {
                startLineY = r.y;
                if (++line == 4) {
                  return r.y;
                }
              }
            }
          }
          catch (BadLocationException ignored) {
          }

          return (int)(pane.getUI().getRootView(pane).getPreferredSpan(View.Y_AXIS) + JBUI.scale(2f));
        });
      }
      else {

        addDescriptionComponent(centerPanel, getShortDescription(plugin), null);
      }

      if (update && plugin instanceof PluginNode) {
        String version = StringUtil.defaultIfEmpty(myPlugin.getVersion(), null);
        String date = getLastUpdatedDate(myPlugin);

        if (version != null || date != null) {
          int offset = JBUI.scale(8);
          JPanel panel = new NonOpaquePanel(new HorizontalLayout(offset));
          panel.setBorder(JBUI.Borders.emptyLeft(offset));
          nameButtons.add(panel, BorderLayout.CENTER);

          if (version != null) {
            myVersion = new JLabel("Version " + version);
            myVersion.setOpaque(false);
            panel.add(installTiny(myVersion));
          }

          if (date != null) {
            myLastUpdated = new JLabel(date, AllIcons.Plugins.Updated, SwingConstants.CENTER);
            myLastUpdated.setOpaque(false);
            panel.add(installTiny(myLastUpdated));
          }
        }
      }

      setSelection(SelectionType.NONE);
    }

    @Nullable
    private static String getChangeNotes(@NotNull IdeaPluginDescriptor plugin) {
      String notes = plugin.getChangeNotes();
      return StringUtil.isEmptyOrSpaces(notes) ? null : "<b>Change Notes</b><br>\n" + notes;
    }

    @Override
    protected void updateColors(@NotNull Color grayedFg, @NotNull Color background) {
      super.updateColors(grayedFg, background);
      myIconLabel.setEnabled(mySelection != SelectionType.NONE || myPlugin.isEnabled());

      if (myVersion != null) {
        myVersion.setForeground(grayedFg);
      }
      if (myLastUpdated != null) {
        myLastUpdated.setForeground(grayedFg);
      }

      if (mySelection == SelectionType.NONE && !myPlugin.isEnabled()) {
        Color disabledColor = DarculaButtonUI.getDisabledTextColor();
        myName.setForeground(disabledColor);
        if (myDescription != null) {
          myDescription.setForeground(disabledColor);
        }
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
      if (myUpdateButton != null) {
        myUpdateButton.addMouseListener(listener);
        myUpdateButton.addMouseMotionListener(listener);
      }
      if (myEnableDisableButton != null) {
        myEnableDisableButton.addMouseListener(listener);
        myEnableDisableButton.addMouseMotionListener(listener);
      }
      if (myEnableDisableUninstallButton != null) {
        for (JButton button : UIUtil.findComponentsOfType(myEnableDisableUninstallButton, JButton.class)) {
          button.addMouseListener(listener);
          button.addMouseMotionListener(listener);
        }
      }
    }

    @Override
    public void removeMouseListeners(@NotNull MouseAdapter listener) {
      super.removeMouseListeners(listener);
      if (myUpdateButton != null) {
        myUpdateButton.removeMouseListener(listener);
        myUpdateButton.removeMouseMotionListener(listener);
      }
      if (myEnableDisableButton != null) {
        myEnableDisableButton.removeMouseListener(listener);
        myEnableDisableButton.removeMouseMotionListener(listener);
      }
      if (myEnableDisableUninstallButton != null) {
        for (JButton button : UIUtil.findComponentsOfType(myEnableDisableUninstallButton, JButton.class)) {
          button.removeMouseListener(listener);
          button.removeMouseMotionListener(listener);
        }
      }
    }
  }

  private static class GridCellPluginComponent extends CellPluginComponent {
    private JLabel myLastUpdated;
    private JLabel myDownloads;
    private JLabel myRating;
    private final JButton myInstallButton;

    private List<TagComponent> myTagComponents = Collections.emptyList();

    public GridCellPluginComponent(@NotNull IdeaPluginDescriptor plugin, @NotNull TagBuilder tagBuilder) {
      super(plugin);

      JPanel container = new NonOpaquePanel();
      JPanel centerPanel = createPanel(container, this, JBUI.scale(180));
      setBorder(JBUI.Borders.empty(10, 10, 20, 10));

      addNameComponent(centerPanel, null);
      addTags(centerPanel, tagBuilder);
      addDescriptionComponent(centerPanel, getShortDescription(myPlugin), null);

      if (plugin instanceof PluginNode) {
        String downloads = getDownloads(myPlugin);
        String date = getLastUpdatedDate(myPlugin);
        String rating = getRating(myPlugin);

        if (downloads != null || date != null || rating != null) {
          JPanel panel = new NonOpaquePanel(new HorizontalLayout(offset5()));
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

      myInstallButton = new InstallButton(false);
      add(container);
      add(myInstallButton);

      setLayout(new AbstractLayoutManager() {
        @Override
        public Dimension preferredLayoutSize(Container parent) {
          Dimension size = container.getPreferredSize();
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
          myInstallButton.setBounds(bounds.x + location.x - borderOffset, bounds.y + bounds.height, buttonSize.width, buttonSize.height);
        }
      });

      setSelection(SelectionType.NONE);
    }

    private void addTags(@NotNull JPanel parent, @NotNull TagBuilder tagBuilder) {
      List<String> tags = getTags(myPlugin);
      if (tags.isEmpty()) {
        return;
      }

      NonOpaquePanel panel = new NonOpaquePanel(new HorizontalLayout(offset5()));
      parent.add(panel);

      myTagComponents = new ArrayList<>();

      for (String tag : tags) {
        TagComponent component = tagBuilder.createTagComponent(tag);
        myTagComponents.add(installTiny(component));
        panel.add(component);
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
    public void addMouseListeners(@NotNull MouseAdapter listener) {
      super.addMouseListeners(listener);
      for (TagComponent component : myTagComponents) {
        component.addMouseListener(listener);
        component.addMouseMotionListener(listener);
      }
      myInstallButton.addMouseListener(listener);
      myInstallButton.addMouseMotionListener(listener);
    }

    @Override
    public void removeMouseListeners(@NotNull MouseAdapter listener) {
      super.removeMouseListeners(listener);
      for (TagComponent component : myTagComponents) {
        component.removeMouseListener(listener);
        component.removeMouseMotionListener(listener);
      }
      myInstallButton.removeMouseListener(listener);
      myInstallButton.removeMouseMotionListener(listener);
    }
  }

  private static int offset5() {
    return JBUI.scale(5);
  }

  private enum SelectionType {
    SELECTION, HOVER, NONE
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
    public TagComponent(@NotNull String name, @Nullable String tooltip, @NotNull Color color) {
      setText(name);
      if (tooltip != null) {
        setToolTipText(tooltip);
      }
      setBackground(color);
      setPaintUnderline(false);
      setOpaque(true);
      setBorder(JBUI.Borders.empty(2, 5));
    }
  }

  private interface TagBuilder {
    @NotNull
    TagComponent createTagComponent(@NotNull String tag);
  }

  private static class MyOptionButton extends JBOptionButton {
    public MyOptionButton(Action action, Action option) {
      super(action, new Action[]{option});
    }

    @Override
    public Dimension getPreferredSize() {
      Dimension size = super.getPreferredSize();
      Object width = getClientProperty("ExtraWidth");
      if (width instanceof Integer) {
        size.width += (int)width;
      }
      return size;
    }

    @Override
    public void setBackground(Color bg) {
      super.setBackground(bg);
      int count = getComponentCount();
      for (int i = 0; i < count; i++) {
        getComponent(i).setBackground(bg);
      }
    }
  }

  private static class HorizontalLayout extends AbstractLayoutManager {
    private final int myOffset;

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

      Insets insets = parent.getInsets();
      return new Dimension(width, height + insets.top + insets.bottom);
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

      return new Dimension(myWidth > 0 ? myWidth : width, height);
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
      addWidth(10);
    }
  }

  private static class RestartButton extends InstallButton {
    public RestartButton() {
      super(true);
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
      addWidth(20);
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

    protected final void addWidth(int width) {
      Dimension size = getPreferredSize();
      setPreferredSize(new Dimension(size.width + JBUI.scale(width), size.height));
    }
  }

  @Nullable
  private static String getShortDescription(@NotNull IdeaPluginDescriptor plugin) {
    return PluginSiteUtils.preparePluginDescription(plugin.getDescription());
  }

  public static class PluginSiteUtils {
    private static final Pattern TAG_PATTERN =
      Pattern.compile("</?\\w+((\\s+\\w+(\\s*=\\s*(?:\".*?\"|'.*?'|[\\^'\">\\s]+))?)+\\s*|\\s*)/?>");
    private static final int SHORT_DESC_SIZE = 170;
    private static final Pattern BR_PATTERN = Pattern.compile("<br\\s*/?>");

    @Nullable
    public static String preparePluginDescription(@Nullable String s) {
      if (s == null || s.isEmpty()) {
        return null;
      }
      String description = prepareDescription(s);
      return description.isEmpty() || description.endsWith(".") ? description : description + ".";
    }

    @NotNull
    private static String prepareDescription(@NotNull String s) {
      String[] split = BR_PATTERN.split(s);

      if (split.length > 1) {
        String sanitize = stripTags(split[0]);
        if (sanitize.length() <= SHORT_DESC_SIZE) return sanitize;
      }

      String stripped = stripTags(s);

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
}