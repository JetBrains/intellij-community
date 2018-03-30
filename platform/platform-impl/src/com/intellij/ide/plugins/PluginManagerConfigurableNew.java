// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins;

import com.intellij.icons.AllIcons;
import com.intellij.ide.HelpTooltip;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.options.*;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
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
import java.awt.event.*;
import java.io.IOException;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;
import java.util.regex.Pattern;

import static com.intellij.ide.plugins.PluginManagerCore.getPlugins;

/**
 * @author Alexander Lobas
 */
public class PluginManagerConfigurableNew extends BaseConfigurable
  implements SearchableConfigurable, Configurable.NoScroll, Configurable.NoMargin, Configurable.TopComponentProvider,
             OptionalConfigurable {
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

  private final InstalledPluginsTableModel myPluginsModel = new InstalledPluginsTableModel();

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
    PluginsGroupComponent panel = new PluginsGroupComponent(new PluginsGridLayout(), (aSource, aLinkData) -> listener
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

  @NotNull
  private JComponent createDetailsPanel(@NotNull IdeaPluginDescriptor plugin, boolean update) {
    JPanel panel = new OpaquePanel(new BorderLayout(0, JBUI.scale(32)), mySearchTextField.getTextEditor().getBackground());
    panel.setBorder(JBUI.Borders.empty(15, 20, 0, 0));

    JPanel header = new NonOpaquePanel(new BorderLayout(JBUI.scale(20), 0));
    header.setBorder(JBUI.Borders.emptyRight(20));

    JLabel iconLabel = new JLabel(AllIcons.Plugins.PluginLogo_80);
    iconLabel.setDisabledIcon(AllIcons.Plugins.PluginLogoDisabled_80);
    iconLabel.setVerticalAlignment(SwingConstants.TOP);
    iconLabel.setOpaque(false);
    iconLabel.setEnabled(plugin.isEnabled());
    header.add(iconLabel, BorderLayout.WEST);

    JPanel centerPanel = new NonOpaquePanel(new VerticalLayout(offset5()));
    header.add(centerPanel);

    boolean bundled = plugin.isBundled();

    JPanel buttons = new NonOpaquePanel(new HorizontalLayout(JBUI.scale(6)));
    buttons.setBorder(JBUI.Borders.emptyTop(1));
    if (update) {
      buttons.add(new UpdateButton());
    }
    if (plugin instanceof PluginNode) {
      buttons.add(new InstallButton(true));
    }
    else if (bundled) {
      JButton button = new JButton(plugin.isEnabled() ? "Disable" : "Enable");
      setWidth72(button);
      buttons.add(button);
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
      buttons.add(new MyOptionButton(enableDisableAction, uninstallAction));
    }

    for (Component component : UIUtil.uiChildren(buttons)) {
      component.setBackground(MAIN_BG_COLOR);
    }

    JPanel nameButtons = new NonOpaquePanel(new BorderLayout(offset5(), 0));

    JLabel nameComponent = new JLabel(plugin.getName());
    nameComponent.setOpaque(false);
    Font font = nameComponent.getFont();
    if (font != null) {
      nameComponent.setFont(font.deriveFont(Font.BOLD, 30));
    }
    if (!plugin.isEnabled()) {
      nameComponent.setForeground(DisabledColor);
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
      versionComponent.setBorder(JBUI.Borders.empty(nameBaseline - versionBaseline + JBUI.scale(6), 4, 0, 0));
    }

    List<String> tags = getTags(plugin);
    if (!tags.isEmpty()) {
      NonOpaquePanel tagPanel = new NonOpaquePanel(new HorizontalLayout(JBUI.scale(6)));
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

    if (PluginManagerCore.isIncompatible(plugin) || myPluginsModel.hasProblematicDependencies(plugin.getPluginId())) {
      JPanel errorPanel = new NonOpaquePanel(new HorizontalLayout(JBUI.scale(8)));
      centerPanel.add(errorPanel);

      JLabel errorMessage = new JLabel();
      errorMessage.setForeground(JBColor.red);
      errorMessage.setOpaque(false);
      errorPanel.add(errorMessage);

      Ref<Boolean> enableAction = new Ref<>();
      errorMessage.setText(getErrorMessage(myPluginsModel, plugin, enableAction));

      if (!enableAction.isNull()) {
        LinkLabel errorAction = new LinkLabel("Enable", null);
        errorPanel.add(errorAction);
      }
    }

    panel.add(header, BorderLayout.NORTH);

    String description = getDescriptionAndChangeNotes(plugin);
    String vendor = bundled ? null : plugin.getVendor();
    String size = plugin instanceof PluginNode ? ((PluginNode)plugin).getSize() : null;

    if (!StringUtil.isEmptyOrSpaces(description) || !StringUtil.isEmptyOrSpaces(vendor) || !StringUtil.isEmptyOrSpaces(size)) {
      JPanel bottomPanel = new OpaquePanel(new VerticalLayout(offset5()), MAIN_BG_COLOR);
      bottomPanel.setBorder(JBUI.Borders.emptyBottom(15));

      JBScrollPane scrollPane = new JBScrollPane(bottomPanel);
      scrollPane.getVerticalScrollBar().setBackground(MAIN_BG_COLOR);
      scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
      scrollPane.setBorder(null);
      panel.add(scrollPane);

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

  @Nullable
  private static String getDescriptionAndChangeNotes(@NotNull IdeaPluginDescriptor plugin) {
    StringBuilder result = new StringBuilder();

    String description = plugin.getDescription();
    if (!StringUtil.isEmptyOrSpaces(description)) {
      result.append(description);
    }

    String notes = plugin.getChangeNotes();
    if (!StringUtil.isEmptyOrSpaces(notes)) {
      result.append("<h4>Change Notes</h4>").append(notes);
    }

    return result.length() > 0 ? result.toString() : null;
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
        pluginComponent.myIconLabel.setListener(myListener, descriptor);
        //noinspection unchecked
        pluginComponent.myName.setListener(myListener, descriptor);
      }
    }
  }

  private static class PluginsListLayout extends AbstractLayoutManager {
    private final int myFirsVtOffset = JBUI.scale(6);
    private final int myLastVOffset = JBUI.scale(18);

    private int myLineHeight;

    @Override
    public Dimension preferredLayoutSize(@NotNull Container parent) {
      calculateLineHeight(parent);

      List<UIPluginGroup> groups = ((PluginsGroupComponent)parent).myGroups;
      int height = 0;

      for (UIPluginGroup group : groups) {
        height += group.panel.getPreferredSize().height;
        height += group.plugins.size() * myLineHeight;
        height += myFirsVtOffset + myLastVOffset;
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
        y += height + myFirsVtOffset;

        for (CellPluginComponent plugin : group.plugins) {
          plugin.setBounds(0, y, width, myLineHeight);
          y += myLineHeight;
        }

        y += myLastVOffset;
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
    public LinkLabel rightAction;
    public java.util.List<IdeaPluginDescriptor> descriptors = new ArrayList<>();

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
    protected JPanel createPanel(int offset) {
      return createPanel(this, offset5(), offset, 0);
    }

    @NotNull
    protected JPanel createPanel(@NotNull JPanel parent, int hgap, int offset, int width) {
      parent.setLayout(new BorderLayout(hgap, 0));

      myIconLabel = new LinkLabel(null, AllIcons.Plugins.PluginLogo_40);
      myIconLabel.setVerticalAlignment(SwingConstants.TOP);
      myIconLabel.setOpaque(false);
      parent.add(myIconLabel, BorderLayout.WEST);
      myMouseComponents.add(myIconLabel);

      JPanel centerPanel = new NonOpaquePanel(new VerticalLayout(offset, width));
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
    public void paintComponent(@NotNull JEditorPane pane, @NotNull Graphics g) {
    }

    public int getHeight(@NotNull JEditorPane pane) {
      return (int)(pane.getUI().getRootView(pane).getPreferredSpan(View.Y_AXIS) + JBUI.scale(2f));
    }
  }

  private static class ThreeLineFunction extends LineFunction {
    protected Point myLastPoint;

    @Override
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
            if (++line == 4) {
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

      return super.getHeight(pane);
    }
  }

  private static class ListPluginComponent extends CellPluginComponent {
    private final InstalledPluginsTableModel myPluginsModel;

    private JLabel myVersion;
    private JLabel myLastUpdated;
    private JButton myUpdateButton;
    private JButton myEnableDisableButton;
    private JBOptionButton myEnableDisableUninstallButton;
    private final JPanel myNameButtons;
    private JPanel myVersionPanel;
    private JPanel myErrorPanel;

    public ListPluginComponent(@NotNull InstalledPluginsTableModel pluginsModel, @NotNull IdeaPluginDescriptor plugin, boolean update) {
      super(plugin);
      myPluginsModel = pluginsModel;

      JPanel buttons = new NonOpaquePanel(new HorizontalLayout(JBUI.scale(6)));
      if (update) {
        myUpdateButton = new UpdateButton();
        buttons.add(myUpdateButton);
        myMouseComponents.add(myUpdateButton);
      }
      if (plugin.isBundled()) {
        myEnableDisableButton = new JButton(plugin.isEnabled() ? "Disable" : "Enable");
        setWidth72(myEnableDisableButton);
        buttons.add(myEnableDisableButton);
        myMouseComponents.add(myEnableDisableButton);
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

      JPanel centerPanel = createPanel(0);

      myNameButtons = new NonOpaquePanel(new BorderLayout());
      myNameButtons.add(buttons, BorderLayout.EAST);
      centerPanel.add(myNameButtons, VerticalLayout.FILL_HORIZONTAL);

      myIconLabel.setDisabledIcon(AllIcons.Plugins.PluginLogoDisabled_40);

      addNameComponent(myNameButtons, BorderLayout.WEST);
      myName.setVerticalAlignment(SwingConstants.TOP);

      if (update) {
        addDescriptionComponent(centerPanel, getChangeNotes(plugin), new ThreeLineFunction());
      }
      else {
        addDescriptionComponent(centerPanel, getShortDescription(plugin, true), new LineFunction());
      }

      if (update && plugin instanceof PluginNode) {
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

      updateErrors();
      setSelection(SelectionType.NONE);
    }

    @NotNull
    private JPanel createNameBaselinePanel() {
      int offset = JBUI.scale(8);
      JPanel panel = new NonOpaquePanel(new HorizontalLayout(offset) {
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
      });
      panel.setBorder(JBUI.Borders.emptyLeft(offset));
      return panel;
    }

    private void updateErrors() {
      if (PluginManagerCore.isIncompatible(myPlugin) || myPluginsModel.hasProblematicDependencies(myPlugin.getPluginId())) {
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
      myIconLabel.setEnabled(mySelection != SelectionType.NONE || myPlugin.isEnabled());

      if (myVersion != null) {
        myVersion.setForeground(grayedFg);
      }
      if (myLastUpdated != null) {
        myLastUpdated.setForeground(grayedFg);
      }

      if (!myPlugin.isEnabled()) {
        myName.setForeground(mySelection == SelectionType.NONE ? DisabledColor : null);
        if (myDescription != null) {
          myDescription.setForeground(mySelection == SelectionType.NONE ? DisabledColor : null);
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
  }

  private static class GridCellPluginComponent extends CellPluginComponent {
    private JLabel myLastUpdated;
    private JLabel myDownloads;
    private JLabel myRating;
    private final JButton myInstallButton;

    public GridCellPluginComponent(@NotNull IdeaPluginDescriptor plugin, @NotNull TagBuilder tagBuilder) {
      super(plugin);

      JPanel container = new NonOpaquePanel();
      JPanel centerPanel = createPanel(container, JBUI.scale(10), offset5(), JBUI.scale(180));

      addNameComponent(centerPanel, null);
      addTags(centerPanel, tagBuilder);
      addDescriptionComponent(centerPanel, getShortDescription(myPlugin, false), new ThreeLineFunction() {
        @Override
        public void paintComponent(@NotNull JEditorPane pane, @NotNull Graphics g) {
          if (myLastPoint != null) {
            if (g instanceof Graphics2D) {
              ((Graphics2D)g).setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            }
            g.setColor(pane.getForeground());
            g.drawString("...", myLastPoint.x, myLastPoint.y + g.getFontMetrics().getAscent());
          }
        }
      });

      if (plugin instanceof PluginNode) {
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

      myInstallButton = new InstallButton(false);
      add(container);
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

      setSelection(SelectionType.NONE);
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
      setForeground(new JBColor(0x787878, 0xBBBBBB));
      setBackground(color);
      setPaintUnderline(false);
      setOpaque(true);
      setBorder(JBUI.Borders.empty(2));
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
}