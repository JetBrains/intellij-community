/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import com.intellij.ide.IdeBundle;
import com.intellij.ide.ui.search.SearchUtil;
import com.intellij.ide.ui.search.SearchableOptionsRegistrar;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.updateSettings.impl.PluginDownloader;
import com.intellij.openapi.updateSettings.impl.UpdateChecker;
import com.intellij.openapi.updateSettings.impl.UpdateSettings;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.*;
import com.intellij.util.concurrency.SwingWorker;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.update.UiNotifyConnector;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.text.html.HTMLDocument;
import javax.swing.text.html.HTMLFrameHyperlinkEvent;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.*;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: stathik
 * Date: Dec 25, 2003
 * Time: 9:47:59 PM
 * To change this template use Options | File Templates.
 */
public abstract class PluginManagerMain implements Disposable {

  public static Logger LOG = Logger.getInstance("#com.intellij.ide.plugins.PluginManagerMain");

  @NonNls private static final String TEXT_PREFIX = "<html><head>" +
                                                    "    <style type=\"text/css\">" +
                                                    "        p {" +
                                                    "            font-family: Arial,serif; font-size: 12pt; margin: 2px 2px" +
                                                    "        }" +
                                                    "    </style>" +
                                                    "</head><body style=\"font-family: Arial,serif; font-size: 12pt; margin: 5px 5px;\">";
  @NonNls private static final String TEXT_SUFIX = "</body></html>";

  @NonNls private static final String HTML_PREFIX = "<a href=\"";
  @NonNls private static final String HTML_SUFIX = "</a>";

  private boolean requireShutdown = false;

  private JPanel myToolbarPanel;
  private JPanel main;

  private JEditorPane myDescriptionTextArea;

  private JPanel myTablePanel;
  protected JPanel myActionsPanel;


  protected PluginTableModel pluginsModel;
  protected PluginTable pluginTable;

  private ActionToolbar myActionToolbar;

  protected final MyPluginsFilter myFilter = new MyPluginsFilter();
  protected PluginManagerUISettings myUISettings;
  private boolean myDisposed = false;

  public PluginManagerMain(
    PluginManagerUISettings uiSettings) {
    myUISettings = uiSettings;
  }

  protected void init() {
    GuiUtils.replaceJSplitPaneWithIDEASplitter(main);

    myDescriptionTextArea.addHyperlinkListener(new MyHyperlinkListener());

    JScrollPane installedScrollPane = createTable();
    installTableActions(pluginTable);

    myTablePanel.add(installedScrollPane, BorderLayout.CENTER);

    myToolbarPanel.setLayout(new BorderLayout());
    myActionToolbar = ActionManager.getInstance().createActionToolbar("PluginManaer", getActionGroup(true), true);
    final JComponent component = myActionToolbar.getComponent();
    myToolbarPanel.add(component, BorderLayout.WEST);
    myToolbarPanel.add(myFilter, BorderLayout.EAST);
  }

  protected abstract JScrollPane createTable();

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
    UiNotifyConnector.doWhenFirstShown(getPluginTable(), new Runnable() {
      public void run() {
        requireShutdown = false;
        TableUtil.ensureSelectionExists(getPluginTable());
      }
    });
  }

  public PluginTable getPluginTable() {
    return pluginTable;
  }


  public PluginTableModel getPluginsModel() {
    return pluginsModel;
  }

  protected void installTableActions(final PluginTable pluginTable) {
    pluginTable.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
      public void valueChanged(ListSelectionEvent e) {
        final IdeaPluginDescriptor[] descriptors = pluginTable.getSelectedObjects();
        pluginInfoUpdate(descriptors != null && descriptors.length == 1 ? descriptors[0] : null,
                         myFilter.getFilter(), myDescriptionTextArea);
        myActionToolbar.updateActionsImmediately();
      }
    });

    PopupHandler.installUnknownPopupHandler(pluginTable, getActionGroup(false), ActionManager.getInstance());

    new MySpeedSearchBar(pluginTable);
  }

  public void setRequireShutdown(boolean val) {
    requireShutdown |= val;
  }

  public ArrayList<IdeaPluginDescriptorImpl> getDependentList(IdeaPluginDescriptorImpl pluginDescriptor) {
    return pluginsModel.dependent(pluginDescriptor);
  }


  protected void modifyPluginsList(ArrayList<IdeaPluginDescriptor> list) {
    IdeaPluginDescriptor[] selected = pluginTable.getSelectedObjects();
    pluginsModel.updatePluginsList(list);
    pluginTable.getRowSorter().setSortKeys(Collections.singletonList(pluginsModel.getDefaultSortKey()));
    //pluginsModel.sort();
    pluginsModel.filter(myFilter.getFilter().toLowerCase());
    if (selected != null) {
      select(selected);
    }
  }

  protected abstract ActionGroup getActionGroup(boolean inToolbar);

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

    new SwingWorker() {
      ArrayList<IdeaPluginDescriptor> list = null;
      final List<String> errorMessages = new ArrayList<String>();

      public Object construct() {
        try {
          list = RepositoryHelper.process(null);
        }
        catch (Exception e) {
          LOG.info(e);
          errorMessages.add(e.getMessage());
        }
        for (String host : UpdateSettings.getInstance().myPluginHosts) {
          if (!acceptHost(host)) continue;
          final ArrayList<PluginDownloader> downloaded = new ArrayList<PluginDownloader>();
          try {
            UpdateChecker.checkPluginsHost(host, downloaded, false, null);
            for (PluginDownloader downloader : downloaded) {
              final PluginNode pluginNode = PluginDownloader.createPluginNode(host, downloader);
              if (pluginNode != null) {
                if (list == null) list = new ArrayList<IdeaPluginDescriptor>();
                list.add(pluginNode);
              }
            }
          }
          catch (ProcessCanceledException ignore) {
          }
          catch (Exception e) {
            LOG.info(e);
            errorMessages.add(e.getMessage());
          }
        }
        return list;
      }

      public void finished() {
        UIUtil.invokeLaterIfNeeded(new Runnable() {
          public void run() {
            if (list != null && errorMessages.isEmpty()) {
              modifyPluginsList(list);
              propagateUpdates(list);
              setDownloadStatus(false);
            }
            else if (!errorMessages.isEmpty()) {
              setDownloadStatus(false);
              if (0 == Messages.showOkCancelDialog(
                IdeBundle.message("error.list.of.plugins.was.not.loaded", StringUtil.join(errorMessages, ", ")),
                IdeBundle.message("title.plugins"),
                CommonBundle.message("button.retry"), CommonBundle.getCancelButtonText(), Messages.getErrorIcon())) {
                loadPluginsFromHostInBackground();
              }
            }
          }
        });
      }
    }.start();
  }

  protected abstract void propagateUpdates(ArrayList<IdeaPluginDescriptor> list);

  protected void setDownloadStatus(boolean status) {
    pluginTable.setPaintBusy(status);
  }

  protected void loadAvailablePlugins() {
    ArrayList<IdeaPluginDescriptor> list;
    try {
      //  If we already have a file with downloaded plugins from the last time,
      //  then read it, load into the list and start the updating process.
      //  Otherwise just start the process of loading the list and save it
      //  into the persistent config file for later reading.
      File file = new File(PathManager.getPluginsPath(), RepositoryHelper.extPluginsFile);
      if (file.exists()) {
        RepositoryContentHandler handler = new RepositoryContentHandler();
        SAXParser parser = SAXParserFactory.newInstance().newSAXParser();
        parser.parse(file, handler);
        list = handler.getPluginsList();
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
                                        final List<IdeaPluginDescriptor> allPlugins,
                                        final Runnable onSuccess,
                                        final Runnable cleanup) throws IOException {
    final boolean[] result = new boolean[1];
    try {
      ProgressManager.getInstance().run(new Task.Backgroundable(null, IdeBundle.message("progress.download.plugins"), true) {
        @Override
        public void run(@NotNull ProgressIndicator indicator) {
          try {
            if (PluginInstaller.prepareToInstall(plugins, allPlugins)) {
              ApplicationManager.getApplication().invokeLater(onSuccess);
              result[0] = true;
            }
          }
          finally {
            cleanup.run();
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

  private static void setTextValue(@Nullable String val, String filter, JEditorPane pane) {
    if (val != null) {
      pane.setText(SearchUtil.markup(TEXT_PREFIX + val + TEXT_SUFIX, filter).trim());
      pane.setCaretPosition(0);
    }
    else {
      pane.setText(TEXT_PREFIX + TEXT_SUFIX);
    }
  }

  public static void pluginInfoUpdate(Object plugin, @Nullable final String filter, final JEditorPane descriptionTextArea) {
    if (plugin instanceof IdeaPluginDescriptor) {
      IdeaPluginDescriptor pluginDescriptor = (IdeaPluginDescriptor)plugin;

      String description = pluginDescriptor.getDescription();
      String changeNotes = pluginDescriptor.getChangeNotes();
      if (!StringUtil.isEmpty(changeNotes)) {
        description += "<h4>Change Notes</h4>";
        description += changeNotes;
      }

      if (!pluginDescriptor.isBundled()) {
        description += "<h4>Vendor</h4>";
        String vendor = pluginDescriptor.getVendor();
        if (!StringUtil.isEmpty(vendor)) {
          description += vendor;
        }

        String vendorEmail = pluginDescriptor.getVendorEmail();
        if (!StringUtil.isEmpty(vendorEmail)) {
          description += "<br>";
          description += composeHref("mailto:" + vendorEmail);
        }

        String vendorUrl = pluginDescriptor.getVendorUrl();
        if (!StringUtil.isEmpty(vendorUrl)) {
          description += "<br>" + composeHref(vendorUrl);
        }


        String pluginDescriptorUrl = pluginDescriptor.getUrl();
        if (!StringUtil.isEmpty(pluginDescriptorUrl)) {
          description += "<br><h4>Plugin homepage</h4>" + composeHref(pluginDescriptorUrl);
        }

        String version = pluginDescriptor.getVersion();
        if (!StringUtil.isEmpty(version)) {
          description += "<h4>Version</h4>" + version;
        }

        String size = plugin instanceof PluginNode ? ((PluginNode)plugin).getSize() : null;
        if (!StringUtil.isEmpty(size)) {
          description += "<br>Size: " + PluginManagerColumnInfo.getFormattedSize(size);
        }
      }

      setTextValue(description, filter, descriptionTextArea);
    }
    else {
      setTextValue(null, filter, descriptionTextArea);
    }
  }

  private static String composeHref(String vendorUrl) {
    return HTML_PREFIX + vendorUrl + "\">" + vendorUrl + HTML_SUFIX;
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

  public static class MyHyperlinkListener implements HyperlinkListener {
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
            BrowserUtil.launchBrowser(url.toString());
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

    public int getSelectedIndex() {
      return myComponent.getSelectedRow();
    }

    public Object[] getAllElements() {
      return myComponent.getElements();
    }

    public String getElementText(Object element) {
      return ((IdeaPluginDescriptor)element).getName();
    }

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

  protected static boolean isAccepted(String filter,
                                      Set<String> search,
                                      IdeaPluginDescriptor descriptor) {
    if (StringUtil.isEmpty(filter)) return true;
    if (isAccepted(search, filter, descriptor.getName())) {
      return true;
    }
    else {
      final String description = descriptor.getDescription();
      if (description != null && isAccepted(search, filter, description)) {
        return true;
      }
      final String category = descriptor.getCategory();
      if (category != null && isAccepted(search, filter, category)) {
        return true;
      }
      final String changeNotes = descriptor.getChangeNotes();
      if (changeNotes != null && isAccepted(search, filter, changeNotes)) {
        return true;
      }
    }
    return false;
  }

  private static boolean isAccepted(final Set<String> search,
                                    @NotNull final String filter,
                                    @NotNull final String description) {
    if (StringUtil.containsIgnoreCase(description, filter)) return true;
    final SearchableOptionsRegistrar optionsRegistrar = SearchableOptionsRegistrar.getInstance();
    final HashSet<String> descriptionSet = new HashSet<String>(search);
    descriptionSet.removeAll(optionsRegistrar.getProcessedWords(description));
    if (descriptionSet.isEmpty()) {
      return true;
    }
    return false;
  }

  protected class SortByStatusAction extends ToggleAction {

    protected SortByStatusAction(final String title) {
      super(title, title, AllIcons.ObjectBrowser.SortByType);
    }

    @Override
    public boolean isSelected(AnActionEvent e) {
      return pluginsModel.isSortByStatus();
    }

    @Override
    public void setSelected(AnActionEvent e, boolean state) {
      IdeaPluginDescriptor[] selected = pluginTable.getSelectedObjects();
      pluginsModel.setSortByStatus(state);
      pluginsModel.sort();
      if (selected != null) {
        select(selected);
      }
    }
  }

  public class MyPluginsFilter extends FilterComponent {

    public MyPluginsFilter() {
      super("PLUGIN_FILTER", 5);
    }

    public void filter() {
      pluginsModel.filter(getFilter().toLowerCase());
      TableUtil.ensureSelectionExists(getPluginTable());
    }
  }

  protected class RefreshAction extends DumbAwareAction {
    public RefreshAction() {
      super("Reload List of Plugins", "Reload list of plugins", AllIcons.Actions.Sync);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      loadAvailablePlugins();
      myFilter.setFilter("");
    }
  }
}
