/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.ide.plugins;

import com.intellij.CommonBundle;
import com.intellij.icons.AllIcons;
import com.intellij.ide.BrowserUtil;
import com.intellij.ide.DataManager;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.plugins.sorters.SortByStatusAction;
import com.intellij.ide.ui.search.SearchUtil;
import com.intellij.ide.ui.search.SearchableOptionsRegistrar;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.ex.ApplicationEx;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.updateSettings.impl.UpdateChecker;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.*;
import com.intellij.ui.border.CustomLineBorder;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.speedSearch.SpeedSearchSupply;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.update.UiNotifyConnector;
import com.intellij.xml.util.XmlStringUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.event.*;
import javax.swing.plaf.BorderUIResource;
import javax.swing.text.html.HTMLDocument;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.HTMLFrameHyperlinkEvent;
import javax.swing.text.html.StyleSheet;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URL;
import java.util.*;
import java.util.List;

import static com.intellij.openapi.util.text.StringUtil.isEmptyOrSpaces;

/**
 * @author stathik
 * @author Konstantin Bulenkov
 */
public abstract class PluginManagerMain implements Disposable {
  public static final String JETBRAINS_VENDOR = "JetBrains";

  public static Logger LOG = Logger.getInstance("#com.intellij.ide.plugins.PluginManagerMain");

  @NonNls private static final String TEXT_SUFFIX = "</body></html>";

  @NonNls private static final String HTML_PREFIX = "<a href=\"";
  @NonNls private static final String HTML_SUFFIX = "</a>";

  private boolean requireShutdown = false;

  private JPanel myToolbarPanel;
  private JPanel main;

  private JEditorPane myDescriptionTextArea;

  private JPanel myTablePanel;
  protected JPanel myActionsPanel;
  private JPanel myHeader;
  private PluginHeaderPanel myPluginHeaderPanel;
  private JPanel myInfoPanel;
  protected JBLabel myPanelDescription;
  private JBScrollPane myDescriptionScrollPane;


  protected PluginTableModel pluginsModel;
  protected PluginTable pluginTable;

  private ActionToolbar myActionToolbar;

  protected final MyPluginsFilter myFilter = new MyPluginsFilter();
  protected PluginManagerUISettings myUISettings;
  private boolean myDisposed = false;
  private boolean myBusy = false;

  public PluginManagerMain(
    PluginManagerUISettings uiSettings) {
    myUISettings = uiSettings;
  }

  public static boolean isDevelopedByJetBrains(@NotNull IdeaPluginDescriptor plugin) {
    return isDevelopedByJetBrains(plugin.getVendor());
  }

  public static boolean isDevelopedByJetBrains(@Nullable String vendorString) {
    if (vendorString == null) return false;
    for (String vendor : StringUtil.split(vendorString, ",")) {
      if (vendor.trim().equals(JETBRAINS_VENDOR)) {
        return true;
      }
    }
    return false;
  }

  protected void init() {
    GuiUtils.replaceJSplitPaneWithIDEASplitter(main);
    HTMLEditorKit kit = new HTMLEditorKit();
    StyleSheet sheet = kit.getStyleSheet();
    sheet.addRule("ul {margin-left: 16px}"); // list-style-type: none;
    myDescriptionTextArea.setEditorKit(kit);
    myDescriptionTextArea.setEditable(false);
    myDescriptionTextArea.addHyperlinkListener(new MyHyperlinkListener());

    JScrollPane installedScrollPane = createTable();
    myPluginHeaderPanel = new PluginHeaderPanel(this);
    myHeader.setBackground(UIUtil.getTextFieldBackground());
    myPluginHeaderPanel.getPanel().setBackground(UIUtil.getTextFieldBackground());
    myPluginHeaderPanel.getPanel().setOpaque(true);

    myHeader.add(myPluginHeaderPanel.getPanel(), BorderLayout.CENTER);
    installTableActions();

    myTablePanel.add(installedScrollPane, BorderLayout.CENTER);
    UIUtil.applyStyle(UIUtil.ComponentStyle.SMALL, myPanelDescription);
    myPanelDescription.setBorder(JBUI.Borders.emptyLeft(7));

    final JPanel header = new JPanel(new BorderLayout()) {
      @Override
      protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        final Color bg = UIUtil.getTableBackground(false);
        ((Graphics2D)g).setPaint(new GradientPaint(0, 0, ColorUtil.shift(bg, 1.4), 0, getHeight(), ColorUtil.shift(bg, 0.9)));
        g.fillRect(0,0, getWidth(), getHeight());
      }
    };
    header.setBorder(new CustomLineBorder(1, 1, 0, 1));
    final JLabel mySortLabel = new JLabel();
    mySortLabel.setForeground(UIUtil.getLabelDisabledForeground());
    mySortLabel.setBorder(JBUI.Borders.empty(1, 1, 1, 5));
    mySortLabel.setIcon(AllIcons.General.SplitDown);
    mySortLabel.setHorizontalTextPosition(SwingConstants.LEADING);
    header.add(mySortLabel, BorderLayout.EAST);
    myTablePanel.add(header, BorderLayout.NORTH);
    myToolbarPanel.setLayout(new BorderLayout());
    myActionToolbar = ActionManager.getInstance().createActionToolbar("PluginManager", getActionGroup(true), true);
    final JComponent component = myActionToolbar.getComponent();
    myToolbarPanel.add(component, BorderLayout.CENTER);
    myToolbarPanel.add(myFilter, BorderLayout.WEST);
    new ClickListener() {
      @Override
      public boolean onClick(@NotNull MouseEvent event, int clickCount) {
        JBPopupFactory.getInstance().createActionGroupPopup("Sort by:", createSortersGroup(), DataManager.getInstance().getDataContext(pluginTable),
                                                            JBPopupFactory.ActionSelectionAid.SPEEDSEARCH, true).showUnderneathOf(mySortLabel);
        return true;
      }
    }.installOn(mySortLabel);
    final TableModelListener modelListener = new TableModelListener() {
      @Override
      public void tableChanged(TableModelEvent e) {
        String text = "Sort by:";
        if (pluginsModel.isSortByStatus()) {
          text += " status,";
        }
        if (pluginsModel.isSortByRating()) {
          text += " rating,";
        }
        if (pluginsModel.isSortByDownloads()) {
          text += " downloads,";
        }
        if (pluginsModel.isSortByUpdated()) {
          text += " updated,";
        }
        text += " name";
        mySortLabel.setText(text);
      }
    };
    pluginTable.getModel().addTableModelListener(modelListener);
    modelListener.tableChanged(null);

    myDescriptionScrollPane.setBackground(UIUtil.getTextFieldBackground());
    Border border = new BorderUIResource.LineBorderUIResource(new JBColor(Gray._220, Gray._55), 1);
    myInfoPanel.setBorder(border);
  }

  protected abstract JScrollPane createTable();

  @Override
  public void dispose() {
    myDisposed = true;
  }

  public boolean isDisposed() {
    return myDisposed;
  }

  public void filter(String filter) {
    myFilter.setSelectedItem(filter);
  }

  public void reset() {
    UiNotifyConnector.doWhenFirstShown(getPluginTable(), () -> {
      requireShutdown = false;
      TableUtil.ensureSelectionExists(getPluginTable());
    });
  }

  public PluginTable getPluginTable() {
    return pluginTable;
  }

  @NotNull
  public static List<PluginId> mapToPluginIds(List<IdeaPluginDescriptor> plugins) {
    return ContainerUtil.map(plugins, descriptor -> descriptor.getPluginId());
  }

  private static String getTextPrefix() {
    final int fontSize = JBUI.scale(12);
    final int m1 = JBUI.scale(2);
    final int m2 = JBUI.scale(5);
    return String.format(
           "<html><head>" +
           "    <style type=\"text/css\">" +
           "        p {" +
           "            font-family: Arial,serif; font-size: %dpt; margin: %dpx %dpx" +
           "        }" +
           "    </style>" +
           "</head><body style=\"font-family: Arial,serif; font-size: %dpt; margin: %dpx %dpx;\">",
           fontSize, m1, m1, fontSize, m2, m2);
  }

  public PluginTableModel getPluginsModel() {
    return pluginsModel;
  }

  protected void installTableActions() {
    pluginTable.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
      @Override
      public void valueChanged(ListSelectionEvent e) {
        refresh();
      }
    });

    PopupHandler.installUnknownPopupHandler(pluginTable, getActionGroup(false), ActionManager.getInstance());

    new MySpeedSearchBar(pluginTable);
  }

  public void refresh() {
    IdeaPluginDescriptor[] descriptors = pluginTable.getSelectedObjects();
    IdeaPluginDescriptor plugin = descriptors != null && descriptors.length == 1 ? descriptors[0] : null;
    pluginInfoUpdate(plugin, myFilter.getFilter(), myDescriptionTextArea, myPluginHeaderPanel);
    myActionToolbar.updateActionsImmediately();
    final JComponent parent = (JComponent)myHeader.getParent();
    parent.revalidate();
    parent.repaint();
  }

  public void setRequireShutdown(boolean val) {
    requireShutdown |= val;
  }

  public List<IdeaPluginDescriptorImpl> getDependentList(IdeaPluginDescriptorImpl pluginDescriptor) {
    return pluginsModel.dependent(pluginDescriptor);
  }

  protected void modifyPluginsList(List<IdeaPluginDescriptor> list) {
    IdeaPluginDescriptor[] selected = pluginTable.getSelectedObjects();
    pluginsModel.updatePluginsList(list);
    pluginsModel.filter(myFilter.getFilter().toLowerCase());
    if (selected != null) {
      select(selected);
    }
  }

  protected abstract ActionGroup getActionGroup(boolean inToolbar);

  protected abstract PluginManagerMain getAvailable();
  protected abstract PluginManagerMain getInstalled();

  public JPanel getMainPanel() {
    return main;
  }

  protected boolean acceptHost(String host) {
    return true;
  }
  
  /**
   * Start a new thread which downloads new list of plugins from the site in
   * the background and updates a list of plugins in the table.
   */
  protected void loadPluginsFromHostInBackground() {
    setDownloadStatus(true);

    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      final List<IdeaPluginDescriptor> list = ContainerUtil.newArrayList();
      final Map<String, String> errors = ContainerUtil.newLinkedHashMap();
      ProgressIndicator indicator = new EmptyProgressIndicator();

      List<String> hosts = RepositoryHelper.getPluginHosts();
      Set<PluginId> unique = ContainerUtil.newHashSet();
      for (String host : hosts) {
        try {
          if (host == null || acceptHost(host)) {
            List<IdeaPluginDescriptor> plugins = RepositoryHelper.loadPlugins(host, indicator);
            for (IdeaPluginDescriptor plugin : plugins) {
              if (unique.add(plugin.getPluginId())) {
                list.add(plugin);
              }
            }
          }
        }
        catch (FileNotFoundException e) {
          LOG.info(host, e);
        }
        catch (IOException e) {
          LOG.info(host, e);
          if (host != ApplicationInfoEx.getInstanceEx().getBuiltinPluginsUrl()) {
            errors.put(host, String.format("'%s' for '%s'", e.getMessage(), host));
          }
        }
      }

      UIUtil.invokeLaterIfNeeded(() -> {
        setDownloadStatus(false);

        if (!list.isEmpty()) {
          InstalledPluginsState state = InstalledPluginsState.getInstance();
          for (IdeaPluginDescriptor descriptor : list) {
            state.onDescriptorDownload(descriptor);
          }

          modifyPluginsList(list);
          propagateUpdates(list);
        }

        if (!errors.isEmpty()) {
          String message = IdeBundle.message("error.list.of.plugins.was.not.loaded",
                                             StringUtil.join(errors.keySet(), ", "),
                                             StringUtil.join(errors.values(), ",\n"));
          String title = IdeBundle.message("title.plugins");
          String ok = CommonBundle.message("button.retry"), cancel = CommonBundle.getCancelButtonText();
          if (Messages.showOkCancelDialog(message, title, ok, cancel, Messages.getErrorIcon()) == Messages.OK) {
            loadPluginsFromHostInBackground();
          }
        }
      });
    });
  }

  protected abstract void propagateUpdates(List<IdeaPluginDescriptor> list);

  protected void setDownloadStatus(boolean status) {
    pluginTable.setPaintBusy(status);
    myBusy = status;
  }

  protected void loadAvailablePlugins() {
    try {
      //  If we already have a file with downloaded plugins from the last time,
      //  then read it, load into the list and start the updating process.
      //  Otherwise just start the process of loading the list and save it
      //  into the persistent config file for later reading.
      List<IdeaPluginDescriptor> list = RepositoryHelper.loadCachedPlugins();
      if (list != null) {
        modifyPluginsList(list);
      }
    }
    catch (Exception ex) {
      //  Nothing to do, just ignore - if nothing can be read from the local
      //  file just start downloading of plugins' list from the site.
    }
    loadPluginsFromHostInBackground();
  }

  public static boolean downloadPlugins(final List<PluginNode> plugins,
                                        final List<PluginId> allPlugins,
                                        final Runnable onSuccess,
                                        @Nullable final Runnable cleanup) throws IOException {
    final boolean[] result = new boolean[1];
    try {
      ProgressManager.getInstance().run(new Task.Backgroundable(null, IdeBundle.message("progress.download.plugins"), true, PluginManagerUISettings.getInstance()) {
        @Override
        public void run(@NotNull ProgressIndicator indicator) {
          try {
            if (PluginInstaller.prepareToInstall(plugins, allPlugins, indicator)) {
              ApplicationManager.getApplication().invokeLater(onSuccess);
              result[0] = true;
            }
          }
          finally {
            if (cleanup != null) {
              ApplicationManager.getApplication().invokeLater(cleanup);
            }
          }
        }
      });
    }
    catch (RuntimeException e) {
      if (e.getCause() != null && e.getCause() instanceof IOException) {
        throw (IOException)e.getCause();
      }
      else {
        throw e;
      }
    }
    return result[0];
  }

  public boolean isRequireShutdown() {
    return requireShutdown;
  }

  public void ignoreChanges() {
    requireShutdown = false;
  }

  public static void pluginInfoUpdate(IdeaPluginDescriptor plugin,
                                      @Nullable String filter,
                                      @NotNull JEditorPane descriptionTextArea,
                                      @NotNull PluginHeaderPanel header) {
    if (plugin == null) {
      setTextValue(null, filter, descriptionTextArea);
      header.getPanel().setVisible(false);
      return;
    }
    StringBuilder sb = new StringBuilder();
    header.setPlugin(plugin);
    String description = plugin.getDescription();
    if (!isEmptyOrSpaces(description)) {
      sb.append(description);
    }

    String changeNotes = plugin.getChangeNotes();
    if (!isEmptyOrSpaces(changeNotes)) {
      sb.append("<h4>Change Notes</h4>");
      sb.append(changeNotes);
    }

    if (!plugin.isBundled()) {
      String vendor = plugin.getVendor();
      String vendorEmail = plugin.getVendorEmail();
      String vendorUrl = plugin.getVendorUrl();
      if (!isEmptyOrSpaces(vendor) || !isEmptyOrSpaces(vendorEmail) || !isEmptyOrSpaces(vendorUrl)) {
        sb.append("<h4>Vendor</h4>");

        if (!isEmptyOrSpaces(vendor)) {
          sb.append(vendor);
        }
        if (!isEmptyOrSpaces(vendorUrl)) {
          sb.append("<br>").append(composeHref(vendorUrl));
        }
        if (!isEmptyOrSpaces(vendorEmail)) {
          sb.append("<br>")
            .append(HTML_PREFIX)
            .append("mailto:").append(vendorEmail)
            .append("\">").append(vendorEmail).append(HTML_SUFFIX);
        }
      }

      String pluginDescriptorUrl = plugin.getUrl();
      if (!isEmptyOrSpaces(pluginDescriptorUrl)) {
        sb.append("<h4>Plugin homepage</h4>").append(composeHref(pluginDescriptorUrl));
      }

      String size = plugin instanceof PluginNode ? ((PluginNode)plugin).getSize() : null;
      if (!isEmptyOrSpaces(size)) {
        sb.append("<h4>Size</h4>").append(PluginManagerColumnInfo.getFormattedSize(size));
      }
    }

    setTextValue(sb, filter, descriptionTextArea);
  }

  private static void setTextValue(@Nullable StringBuilder text, @Nullable String filter, JEditorPane pane) {
    if (text != null) {
      text.insert(0, getTextPrefix());
      text.append(TEXT_SUFFIX);
      pane.setText(SearchUtil.markup(text.toString(), filter).trim());
      pane.setCaretPosition(0);
    }
    else {
      pane.setText(getTextPrefix() + TEXT_SUFFIX);
    }
  }

  private static String composeHref(String vendorUrl) {
    return HTML_PREFIX + vendorUrl + "\">" + vendorUrl + HTML_SUFFIX;
  }

  public boolean isModified() {
    if (requireShutdown) return true;
    return false;
  }

  public String apply() {
    final String applyMessage = canApply();
    if (applyMessage != null) return applyMessage;
    setRequireShutdown(true);
    return null;
  }

  @Nullable
  protected String canApply() {
    return null;
  }

  private void createUIComponents() {
    myHeader = new JPanel(new BorderLayout()) {
      @Override
      public Color getBackground() {
        return UIUtil.getTextFieldBackground();
      }
    };
  }

  public static class MyHyperlinkListener implements HyperlinkListener {
    @Override
    public void hyperlinkUpdate(HyperlinkEvent e) {
      if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
        JEditorPane pane = (JEditorPane)e.getSource();
        if (e instanceof HTMLFrameHyperlinkEvent) {
          HTMLFrameHyperlinkEvent evt = (HTMLFrameHyperlinkEvent)e;
          HTMLDocument doc = (HTMLDocument)pane.getDocument();
          doc.processHTMLFrameHyperlinkEvent(evt);
        }
        else {
          URL url = e.getURL();
          if (url != null) {
            BrowserUtil.browse(url);
          }
        }
      }
    }
  }

  private static class MySpeedSearchBar extends SpeedSearchBase<PluginTable> {
    public MySpeedSearchBar(PluginTable cmp) {
      super(cmp);
    }

    @Override
    protected int convertIndexToModel(int viewIndex) {
      return getComponent().convertRowIndexToModel(viewIndex);
    }

    @Override
    public int getSelectedIndex() {
      return myComponent.getSelectedRow();
    }

    @Override
    public Object[] getAllElements() {
      return myComponent.getElements();
    }

    @Override
    public String getElementText(Object element) {
      return ((IdeaPluginDescriptor)element).getName();
    }

    @Override
    public void selectElement(Object element, String selectedText) {
      for (int i = 0; i < myComponent.getRowCount(); i++) {
        if (myComponent.getObjectAt(i).getName().equals(((IdeaPluginDescriptor)element).getName())) {
          myComponent.setRowSelectionInterval(i, i);
          TableUtil.scrollSelectionToVisible(myComponent);
          break;
        }
      }
    }
  }

  public void select(IdeaPluginDescriptor... descriptors) {
    pluginTable.select(descriptors);
  }

  protected static boolean isAccepted(@Nullable String filter, @NotNull Set<String> search, @NotNull IdeaPluginDescriptor descriptor) {
    if (StringUtil.isEmpty(filter)) return true;
    if (StringUtil.containsIgnoreCase(descriptor.getName(), filter) || isAccepted(search, filter, descriptor.getName())) return true;
    if (isAccepted(search, filter, descriptor.getDescription())) return true;
    String category = descriptor.getCategory();
    return category != null && (StringUtil.containsIgnoreCase(category, filter) || isAccepted(search, filter, category));
  }

  private static boolean isAccepted(@NotNull Set<String> search, @NotNull String filter, @Nullable String description) {
    if (StringUtil.isEmpty(description)) return false;
    if (filter.length() <= 2) return false; 
    Set<String> words = SearchableOptionsRegistrar.getInstance().getProcessedWords(description);
    if (words.contains(filter)) return true;
    if (search.isEmpty()) return false;
    Set<String> descriptionSet = new HashSet<>(search);
    descriptionSet.removeAll(words);
    return descriptionSet.isEmpty();
  }

  public static boolean suggestToEnableInstalledDependantPlugins(PluginEnabler pluginEnabler,
                                                                 List<PluginNode> list) {
    final Set<IdeaPluginDescriptor> disabled = new HashSet<>();
    final Set<IdeaPluginDescriptor> disabledDependants = new HashSet<>();
    for (PluginNode node : list) {
      final PluginId pluginId = node.getPluginId();
      if (pluginEnabler.isDisabled(pluginId)) {
        disabled.add(node);
      }
      final List<PluginId> depends = node.getDepends();
      if (depends != null) {
        final Set<PluginId> optionalDeps = new HashSet<>(Arrays.asList(node.getOptionalDependentPluginIds()));
        for (PluginId dependantId : depends) {
          if (optionalDeps.contains(dependantId)) continue;
          final IdeaPluginDescriptor pluginDescriptor = PluginManager.getPlugin(dependantId);
          if (pluginDescriptor != null && pluginEnabler.isDisabled(dependantId)) {
            disabledDependants.add(pluginDescriptor);
          }
        }
      }
    }

    if (!disabled.isEmpty() || !disabledDependants.isEmpty()) {
      String message = "";
      if (disabled.size() == 1) {
        message += "Updated plugin '" + disabled.iterator().next().getName() + "' is disabled.";
      }
      else if (!disabled.isEmpty()) {
        message += "Updated plugins " + StringUtil.join(disabled, pluginDescriptor -> pluginDescriptor.getName(), ", ") + " are disabled.";
      }

      if (!disabledDependants.isEmpty()) {
        message += "<br>";
        message += "Updated plugin" + (list.size() > 1 ? "s depend " : " depends ") + "on disabled";
        if (disabledDependants.size() == 1) {
          message += " plugin '" + disabledDependants.iterator().next().getName() + "'.";
        }
        else {
          message += " plugins " + StringUtil.join(disabledDependants, pluginDescriptor -> pluginDescriptor.getName(), ", ") + ".";
        }
      }
      message += " Disabled plugins " + (disabled.isEmpty() ? "and plugins which depend on disabled " :"") + "won't be activated after restart.";

      int result;
      if (!disabled.isEmpty() && !disabledDependants.isEmpty()) {
        result =
          Messages.showYesNoCancelDialog(XmlStringUtil.wrapInHtml(message), CommonBundle.getWarningTitle(), "Enable all",
                                         "Enable updated plugin" + (disabled.size() > 1 ? "s" : ""), CommonBundle.getCancelButtonText(),
                                         Messages.getQuestionIcon());
        if (result == Messages.CANCEL) return false;
      }
      else {
        message += "<br>Would you like to enable ";
        if (!disabled.isEmpty()) {
          message += "updated plugin" + (disabled.size() > 1 ? "s" : "");
        }
        else {
          //noinspection SpellCheckingInspection
          message += "plugin dependenc" + (disabledDependants.size() > 1 ? "ies" : "y");
        }
        message += "?";
        result = Messages.showYesNoDialog(XmlStringUtil.wrapInHtml(message), CommonBundle.getWarningTitle(), Messages.getQuestionIcon());
        if (result == Messages.NO) return false;
      }

      if (result == Messages.YES) {
        disabled.addAll(disabledDependants);
        pluginEnabler.enablePlugins(disabled);
      }
      else if (result == Messages.NO && !disabled.isEmpty()) {
        pluginEnabler.enablePlugins(disabled);
      }
      return true;
    }
    return false;
  }

  public interface PluginEnabler {
    void enablePlugins(Set<IdeaPluginDescriptor> disabled);

    boolean isDisabled(PluginId pluginId);

    class HEADLESS implements PluginEnabler {
      @Override
      public void enablePlugins(Set<IdeaPluginDescriptor> disabled) {
        for (IdeaPluginDescriptor descriptor : disabled) {
          PluginManagerCore.enablePlugin(descriptor.getPluginId().getIdString());
        }
      }

      @Override
      public boolean isDisabled(PluginId pluginId) {
        return isDisabled(pluginId.getIdString());
      }

      public boolean isDisabled(String pluginId) {
        return PluginManagerCore.getDisabledPlugins().contains(pluginId);
      }
    }

    class UI implements PluginEnabler {
      @NotNull
      private final InstalledPluginsTableModel pluginsModel;

      public UI(@NotNull InstalledPluginsTableModel model) {
        pluginsModel = model;
      }

      @Override
      public void enablePlugins(Set<IdeaPluginDescriptor> disabled) {
        pluginsModel.enableRows(disabled.toArray(new IdeaPluginDescriptor[disabled.size()]), true);
      }

      @Override
      public boolean isDisabled(PluginId pluginId) {
        return pluginsModel.isDisabled(pluginId);
      }
    }
  }

  public static void notifyPluginsUpdated(@Nullable Project project) {
    final ApplicationEx app = ApplicationManagerEx.getApplicationEx();
    String title = IdeBundle.message("update.notifications.title");
    String action = IdeBundle.message(app.isRestartCapable() ? "ide.restart.action" : "ide.shutdown.action");
    String message = IdeBundle.message("ide.restart.required.notification", action, ApplicationNamesInfo.getInstance().getFullProductName());
    NotificationListener listener = new NotificationListener() {
      @Override
      public void hyperlinkUpdate(@NotNull Notification notification, @NotNull HyperlinkEvent event) {
        notification.expire();
        app.restart(true);
      }
    };
    UpdateChecker.NOTIFICATIONS.createNotification(title, XmlStringUtil.wrapInHtml(message), NotificationType.INFORMATION, listener).notify(project);
  }

  public class MyPluginsFilter extends FilterComponent {
    public MyPluginsFilter() {
      super("PLUGIN_FILTER", 5);
    }

    @Override
    public void filter() {
      getPluginTable().putClientProperty(SpeedSearchSupply.SEARCH_QUERY_KEY, getFilter());
      pluginsModel.filter(getFilter().toLowerCase());
      TableUtil.ensureSelectionExists(getPluginTable());
    }
  }

  protected class RefreshAction extends DumbAwareAction {
    public RefreshAction() {
      super("Reload List of Plugins", "Reload list of plugins", AllIcons.Actions.Refresh);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      loadAvailablePlugins();
      myFilter.setFilter("");
    }

    @Override
    public void update(AnActionEvent e) {
      e.getPresentation().setEnabled(!myBusy);
    }
  }

  protected DefaultActionGroup createSortersGroup() {
    final DefaultActionGroup group = new DefaultActionGroup("Sort by", true);
    group.addAction(new SortByStatusAction(pluginTable, pluginsModel));
    return group;
  }
}
