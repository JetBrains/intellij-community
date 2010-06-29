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
import com.intellij.ide.BrowserUtil;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.ui.search.SearchUtil;
import com.intellij.ide.ui.search.SearchableOptionsRegistrar;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.*;
import com.intellij.util.Function;
import com.intellij.util.concurrency.SwingWorker;
import com.intellij.util.net.HTTPProxySettingsDialog;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.update.UiNotifyConnector;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.*;
import javax.swing.text.html.HTMLDocument;
import javax.swing.text.html.HTMLFrameHyperlinkEvent;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.awt.*;
import java.awt.event.*;
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
public class PluginManagerMain implements Disposable {
  public static Logger LOG = Logger.getInstance("#com.intellij.ide.plugins.PluginManagerMain");

  @NonNls private static final String TEXT_PREFIX = "<html><body style=\"font-family: Arial; font-size: 12pt;\">";
  @NonNls private static final String TEXT_SUFIX = "</body></html>";

  @NonNls private static final String HTML_PREFIX = "<html><body><a href=\"\">";
  @NonNls private static final String HTML_SUFIX = "</a></body></html>";

  private boolean requireShutdown = false;

  private JPanel myToolbarPanel;
  private JPanel main;

  private JEditorPane myDescriptionTextArea;
  private JEditorPane myChangeNotesTextArea;
  private JLabel myVendorLabel;
  private JLabel myVendorEmailLabel;
  private JLabel myVendorUrlLabel;
  private JLabel myPluginUrlLabel;
  private JLabel myVersionLabel;
  private JLabel mySizeLabel;
  private JButton myHttpProxySettingsButton;
  private JProgressBar myProgressBar;
  private JButton btnCancel;
  private JLabel mySynchStatus;
  private JPanel myTablePanel;
  private JButton myReloadButton;
  private final TabbedPaneWrapper myTabbedPane;

  private DefaultActionGroup actionGroup;

  private final InstalledPluginsTableModel installedPluginsModel;
  private final PluginTable installedPluginTable;

  private final AvailablePluginsTableModel availablePluginsModel;
  private final PluginTable availablePluginsTable;
  private ArrayList<IdeaPluginDescriptor> pluginsList;
  private final ActionToolbar myActionToolbar;

  private final MyPluginsFilter myFilter = new MyPluginsFilter();

  public PluginManagerMain(final SortableProvider installedProvider, final SortableProvider availableProvider) {
    myDescriptionTextArea.addHyperlinkListener(new MyHyperlinkListener());
    myChangeNotesTextArea.addHyperlinkListener(new MyHyperlinkListener());

    installedPluginsModel = new InstalledPluginsTableModel(installedProvider);
    installedPluginTable = new PluginTable(installedPluginsModel);

    installedPluginTable.setColumnWidth(2, 45);

    JScrollPane installedScrollPane = ScrollPaneFactory.createScrollPane(installedPluginTable);
    installedPluginTable.registerKeyboardAction(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        final int column = InstalledPluginsTableModel.getCheckboxColumn();
        final int[] selectedRows = installedPluginTable.getSelectedRows();
        boolean currentlyMarked = true;
        for (final int selectedRow : selectedRows) {
          if (selectedRow < 0 || !installedPluginTable.isCellEditable(selectedRow, column)) {
            return;
          }
          currentlyMarked &= ((Boolean)installedPluginTable.getValueAt(selectedRow, column)).booleanValue();
        }
        for (int selectedRow : selectedRows) {
          installedPluginTable.setValueAt(currentlyMarked ? Boolean.FALSE : Boolean.TRUE, selectedRow, column);
        }
      }
    }, KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0), JComponent.WHEN_FOCUSED);


    availablePluginsModel = new AvailablePluginsTableModel(availableProvider);
    availablePluginsTable = new PluginTable(availablePluginsModel);
    //  Downloads
    availablePluginsTable.setColumnWidth(1, 70);
    JScrollPane availableScrollPane = ScrollPaneFactory.createScrollPane(availablePluginsTable);

    installTableActions(installedPluginTable);
    installTableActions(availablePluginsTable);

    myHttpProxySettingsButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        HTTPProxySettingsDialog settingsDialog = new HTTPProxySettingsDialog();
        settingsDialog.pack();
        settingsDialog.show();
        if ( settingsDialog.isOK() ) {
          loadAvailablePlugins();
        }
      }
    });

    myReloadButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        loadAvailablePlugins();
        myFilter.setFilter("");
      }
    });

    myTabbedPane = new TabbedPaneWrapper(this);
    myTablePanel.add(myTabbedPane.getComponent(), BorderLayout.CENTER);
    myTablePanel.setMinimumSize(new Dimension(400, -1));
    myTabbedPane.addTab(IdeBundle.message("plugin.status.installed"), installedScrollPane);
    myTabbedPane.addTab(IdeBundle.message("plugin.status.available"), availableScrollPane);
    myTabbedPane.addChangeListener(new ChangeListener() {
      public void stateChanged(ChangeEvent e) {
        final PluginTable pluginTable = getPluginTable();
        TableUtil.ensureSelectionExists(pluginTable);
        pluginInfoUpdate(pluginTable.getSelectedObject());
        myActionToolbar.updateActionsImmediately();
        SwingUtilities.invokeLater(new Runnable() {
          public void run() {
            myFilter.filter();
          }
        });
      }
    });
    //GuiUtils.replaceJSplitPaneWithIDEASplitter(main);

    myToolbarPanel.setLayout(new BorderLayout());
    myActionToolbar = ActionManager.getInstance().createActionToolbar("PluginManaer", getActionGroup(), true);
    myToolbarPanel.add(myActionToolbar.getComponent(), BorderLayout.WEST);
    myToolbarPanel.add(myFilter, BorderLayout.EAST);
  }

  public void dispose() {
  }

  public void filter(String filter) {
    myFilter.setSelectedItem(filter);
  }

  public void reset() {
    UiNotifyConnector.doWhenFirstShown(getPluginTable(), new Runnable() {
      public void run() {
        requireShutdown = false;
        pluginsList = null;
        TableUtil.ensureSelectionExists(getPluginTable());
        loadAvailablePlugins();
      }
    });
  }

  PluginTable getPluginTable() {
    return myTabbedPane.getSelectedIndex() == 0 ? installedPluginTable : availablePluginsTable;
  }


  public PluginTable getInstalledPluginTable() {
    return installedPluginTable;
  }

  public PluginTable getAvailablePluginsTable() {
    return availablePluginsTable;
  }

  public InstalledPluginsTableModel getInstalledPluginsModel() {
    return installedPluginsModel;
  }

  public AvailablePluginsTableModel getAvailablePluginsModel() {
    return availablePluginsModel;
  }

  private void installTableActions(final PluginTable pluginTable) {
    pluginTable.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
      public void valueChanged(ListSelectionEvent e) {
        final IdeaPluginDescriptor[] descriptors = pluginTable.getSelectedObjects();
        pluginInfoUpdate(descriptors != null && descriptors.length == 1 ? descriptors[0] : null);
        myActionToolbar.updateActionsImmediately();
      }
    });

    PopupHandler.installUnknownPopupHandler(pluginTable, getActionGroup(), ActionManager.getInstance());

    myVendorEmailLabel.setCursor(new Cursor(Cursor.HAND_CURSOR));
    myVendorEmailLabel.addMouseListener(new MouseAdapter() {
      public void mouseClicked(MouseEvent e) {
        if (e.isConsumed()) return;
        IdeaPluginDescriptor pluginDescriptor = getPluginTable().getSelectedObject();
        if (pluginDescriptor != null) {
          //noinspection HardCodedStringLiteral
          launchBrowserAction(pluginDescriptor.getVendorEmail(), "mailto:");
          e.consume();
        }
      }
    });

    myVendorUrlLabel.setCursor(new Cursor(Cursor.HAND_CURSOR));
    myVendorUrlLabel.addMouseListener(new MouseAdapter() {
      public void mouseClicked(MouseEvent e) {
        if (e.isConsumed()) return;
        IdeaPluginDescriptor pluginDescriptor = getPluginTable().getSelectedObject();
        if (pluginDescriptor != null) {
          launchBrowserAction(pluginDescriptor.getVendorUrl(), "");
          e.consume();
        }
      }
    });

    myPluginUrlLabel.setCursor(new Cursor(Cursor.HAND_CURSOR));
    myPluginUrlLabel.addMouseListener(new MouseAdapter() {
      public void mouseClicked(MouseEvent e) {
        if (e.isConsumed()) return;
        IdeaPluginDescriptor pluginDescriptor = getPluginTable().getSelectedObject();
        if (pluginDescriptor != null) {
          launchBrowserAction(pluginDescriptor.getUrl(), "");
          e.consume();
        }
      }
    });

    new MySpeedSearchBar(pluginTable);
  }

  public void setRequireShutdown(boolean val) {
    requireShutdown |= val;
  }

  public ArrayList<IdeaPluginDescriptorImpl> getDependentList(IdeaPluginDescriptorImpl pluginDescriptor) {
    return installedPluginsModel.dependent(pluginDescriptor);
  }

  private void loadAvailablePlugins() {
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

  private void modifyPluginsList(ArrayList<IdeaPluginDescriptor> list) {
    IdeaPluginDescriptor[] selected = availablePluginsTable.getSelectedObjects();
    if (pluginsList == null) {
      availablePluginsModel.addData(list);
      installedPluginsModel.addData(list);
    }
    else {
      availablePluginsModel.modifyData(list);
      installedPluginsModel.modifyData(list);
    }
    pluginsList = list;
    if (selected != null) {
      select(selected);
    }
  }

  /**
   * Start a new thread which downloads new list of plugins from the site in
   * the background and updates a list of plugins in the table.
   */
  private void loadPluginsFromHostInBackground() {
    setDownloadStatus(true);

    new SwingWorker() {
      ArrayList<IdeaPluginDescriptor> list = null;
      Exception error;

      public Object construct() {
        try {
          list = RepositoryHelper.process(mySynchStatus);
        }
        catch (Exception e) {
          error = e;
        }
        return list;
      }

      public void finished() {
        UIUtil.invokeLaterIfNeeded(new Runnable() {
          public void run() {
            if (list != null) {
              modifyPluginsList(list);
              setDownloadStatus(false);
            }
            else if (error != null) {
              LOG.info(error);
              setDownloadStatus(false);
              if (0==Messages.showDialog(
                IdeBundle.message("error.list.of.plugins.was.not.loaded", error.getMessage()),
                IdeBundle.message("title.plugins"),
                new String[]{CommonBundle.message("button.retry"), CommonBundle.getCancelButtonText()}, 0, Messages.getErrorIcon())) {
                loadPluginsFromHostInBackground();
              }
            }
          }
        });
      }
    }.start();
  }

  private void setDownloadStatus(boolean what) {
    btnCancel.setVisible(what);
    mySynchStatus.setVisible(what);
    myProgressBar.setVisible(what);
    myProgressBar.setEnabled(what);
    myProgressBar.setIndeterminate(what);
  }

  private ActionGroup getActionGroup() {
    if (actionGroup == null) {
      actionGroup = new DefaultActionGroup();
      actionGroup.add(new ActionInstallPlugin(this));
      actionGroup.add(new ActionUninstallPlugin(this, installedPluginTable));
    }
    return actionGroup;
  }

  public JPanel getMainPanel() {
    return main;
  }

  public static boolean downloadPlugins(final List<PluginNode> plugins) throws IOException {
    final boolean[] result = new boolean[1];
    try {
      ProgressManager.getInstance().runProcessWithProgressSynchronously(new Runnable() {
        public void run() {
          result[0] = PluginInstaller.prepareToInstall(plugins);
        }
      }, IdeBundle.message("progress.download.plugins"), true, null);
    }
    catch (RuntimeException e) {
      if (e.getCause() != null && e.getCause() instanceof IOException) {
        throw(IOException)e.getCause();
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

  private static void setTextValue(String val, JEditorPane pane) {
    if (val != null) {
      pane.setText(TEXT_PREFIX + val.trim() + TEXT_SUFIX);
      pane.setCaretPosition(0);
    }
    else {
      pane.setText(TEXT_PREFIX + TEXT_SUFIX);
    }
  }

  private static void setTextValue(String val, JLabel label) {
    label.setText((val != null) ? val : "");
  }

  private static void setHtmlValue(final String val, JLabel label) {
    boolean isValid = (val != null && val.trim().length() > 0);
    String setVal = isValid ? HTML_PREFIX + val.trim() + HTML_SUFIX : IdeBundle.message("plugin.status.not.specified");

    label.setText(setVal);
    label.setCursor(isValid ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) : Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
  }

  private void pluginInfoUpdate(Object plugin) {
    if (plugin != null) {
      IdeaPluginDescriptor pluginDescriptor = (IdeaPluginDescriptor)plugin;

      myVendorLabel.setText(pluginDescriptor.getVendor());
      final String description = pluginDescriptor.getDescription();
      setTextValue(description != null ? SearchUtil.markup(description, myFilter.getFilter()) : null, myDescriptionTextArea);
      setTextValue(pluginDescriptor.getChangeNotes(), myChangeNotesTextArea);
      setHtmlValue(pluginDescriptor.getVendorEmail(), myVendorEmailLabel);
      setHtmlValue(pluginDescriptor.getVendorUrl(), myVendorUrlLabel);
      setHtmlValue(pluginDescriptor.getUrl(), myPluginUrlLabel);
      setTextValue(pluginDescriptor.getVersion(), myVersionLabel);

      String size = plugin instanceof PluginNode ? ((PluginNode)plugin).getSize() : null;
      if (size != null) {
        size = PluginManagerColumnInfo.getFormattedSize(size);
      }
      setTextValue(size, mySizeLabel);
    } else {
      myVendorLabel.setText("");
      setTextValue(null, myDescriptionTextArea);
      setTextValue(null, myChangeNotesTextArea);
      myVendorEmailLabel.setText("");
      myVendorUrlLabel.setText("");
      myPluginUrlLabel.setText("");
      myVersionLabel.setText("");
      mySizeLabel.setText("");
    }
  }

  private static void launchBrowserAction(String cmd, String prefix) {
    if (cmd != null && cmd.trim().length() > 0) {
      try {
        BrowserUtil.launchBrowser(prefix + cmd.trim());
      }
      catch (IllegalThreadStateException ex) {
        /* not a problem */
      }
    }
  }

  public boolean isModified() {
    if (requireShutdown) return true;
    for (int i = 0; i < installedPluginsModel.getRowCount(); i++) {
      final IdeaPluginDescriptorImpl pluginDescriptor = (IdeaPluginDescriptorImpl)installedPluginsModel.getObjectAt(i);
      if (pluginDescriptor.isEnabled() != installedPluginsModel.getEnabledMap().get(pluginDescriptor.getPluginId()).booleanValue()) return true;
    }
    for (IdeaPluginDescriptor descriptor : myFilter.getFilteredInstalled()) {
      if (((IdeaPluginDescriptorImpl)descriptor).isEnabled() != installedPluginsModel.getEnabledMap().get(descriptor.getPluginId()).booleanValue()) return true;
    }
    return false;
  }

  public void apply() {
    if (!canApply()) return;
    setRequireShutdown(true);
    for (int i = 0; i< installedPluginTable.getRowCount(); i++) {
      final IdeaPluginDescriptorImpl pluginDescriptor = (IdeaPluginDescriptorImpl)installedPluginsModel.getObjectAt(i);
      pluginDescriptor.setEnabled(((Boolean)installedPluginsModel.getValueAt(i, InstalledPluginsTableModel.getCheckboxColumn())).booleanValue());
    }
    for (IdeaPluginDescriptor descriptor : myFilter.getFilteredInstalled()) {
      ((IdeaPluginDescriptorImpl)descriptor).setEnabled(installedPluginsModel.getEnabledMap().get(descriptor.getPluginId()).booleanValue());
    }
    try {
      final ArrayList<String> ids = new ArrayList<String>();
      for (Map.Entry<PluginId, Boolean> entry : installedPluginsModel.getEnabledMap().entrySet()) {
        if (!entry.getValue().booleanValue()) {
          ids.add(entry.getKey().getIdString());
        }
      }
      PluginManager.saveDisabledPlugins(ids, false);
    }
    catch (IOException e) {
      LOG.error(e);
    }
  }

  private boolean canApply() {
    final StringBuilder buf = new StringBuilder();
    for (Map.Entry<PluginId, Boolean> entry : installedPluginsModel.getEnabledMap().entrySet()) {
      final IdeaPluginDescriptorImpl pluginDescriptor = (IdeaPluginDescriptorImpl)PluginManager.getPlugin(entry.getKey());
      if (entry.getValue().booleanValue()) {
        final Set<PluginId> absent = new HashSet<PluginId>();
        PluginManager.checkDependants(pluginDescriptor, new Function<PluginId, IdeaPluginDescriptor>() {
          @Nullable
          public IdeaPluginDescriptor fun(final PluginId pluginId) {
            return PluginManager.getPlugin(pluginId);
          }
        }, new Condition<PluginId>() {
          public boolean value(final PluginId pluginId) {
            if (!installedPluginsModel.isEnabled(pluginId)) {
              absent.add(pluginId);
              return false;
            }
            return true;
          }
        });
        if (!absent.isEmpty()) {
          buf.append(IdeBundle.message("disabled.plugins.warning.message", pluginDescriptor.getName(),
                                       StringUtil.join(absent, new Function<PluginId, String>() {
                                         public String fun(final PluginId pluginId) {
                                           final IdeaPluginDescriptor ideaPluginDescriptor = PluginManager.getPlugin(pluginId);
                                           return "\"" + (ideaPluginDescriptor != null ? ideaPluginDescriptor.getName() : pluginId.getIdString()) + "\"";
                                         }
                                       }, ", "), absent.size()));
        }
      }
    }
    if (buf.length() > 0) {
      @NonNls final String message = "<html><body><ul>" + buf.toString() + "</ul>" +
                                     "Changes won't be applied." +
                                     "</body></html>";
      Messages.showErrorDialog(main, message);
      return false;
    }
    return true;
  }

  private static class MyHyperlinkListener implements HyperlinkListener {
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
          if( url != null )
            BrowserUtil.launchBrowser( url.toString() );
        }
      }
    }
  }

  private static class MySpeedSearchBar extends SpeedSearchBase<PluginTable> {
    public MySpeedSearchBar(PluginTable cmp) {
      super(cmp);
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
    installedPluginTable.select(descriptors);
  }

  private class MyPluginsFilter extends FilterComponent {
    private final List<IdeaPluginDescriptor> myFilteredInstalled = new ArrayList<IdeaPluginDescriptor>();
    private final List<IdeaPluginDescriptor> myFilteredAvailable = new ArrayList<IdeaPluginDescriptor>();

    public MyPluginsFilter() {
      super("PLUGIN_FILTER", 5);
    }

    public void filter() {
      if (installedPluginTable.isShowing()) {
        filter(installedPluginsModel, myFilteredInstalled);
        if (pluginsList != null) {
          installedPluginsModel.modifyData(pluginsList);
        }
      }
      else {
        filter(availablePluginsModel, myFilteredAvailable);
      }
    }

    public List<IdeaPluginDescriptor> getFilteredInstalled() {
      return myFilteredInstalled;
    }

    private void filter(PluginTableModel model, final List<IdeaPluginDescriptor> filtered) {
      final String filter = getFilter().toLowerCase();
      final SearchableOptionsRegistrar optionsRegistrar = SearchableOptionsRegistrar.getInstance();
      final Set<String> search = optionsRegistrar.getProcessedWords(filter);
      final ArrayList<IdeaPluginDescriptor> current = new ArrayList<IdeaPluginDescriptor>();
      final List<IdeaPluginDescriptor> view = model.view;
      final LinkedHashSet<IdeaPluginDescriptor> toBeProcessed = new LinkedHashSet<IdeaPluginDescriptor>(view);
      toBeProcessed.addAll(filtered);
      filtered.clear();
      for (IdeaPluginDescriptor descriptor : toBeProcessed) {
        if (descriptor.getName().toLowerCase().indexOf(filter) != -1) {
          current.add(descriptor);
          continue;
        }
        if (isAccepted(search, current, descriptor, descriptor.getName())) {
          continue;
        }
        else {
          final String description = descriptor.getDescription();
          if (description != null && isAccepted(search, current, descriptor, description)) {
            continue;
          }
          final String category = descriptor.getCategory();
          if (category != null && isAccepted(search, current, descriptor, category)) {
            continue;
          }
          final String changeNotes = descriptor.getChangeNotes();
          if (changeNotes != null && isAccepted(search, current, descriptor, changeNotes)) {
            continue;
          }
        }
        filtered.add(descriptor);
      }
      model.filter(current);
    }

    private boolean isAccepted(final Set<String> search,
                               final ArrayList<IdeaPluginDescriptor> current,
                               final IdeaPluginDescriptor descriptor,
                               final String description) {
      final SearchableOptionsRegistrar optionsRegistrar = SearchableOptionsRegistrar.getInstance();
      final HashSet<String> descriptionSet = new HashSet<String>(search);
      descriptionSet.removeAll(optionsRegistrar.getProcessedWords(description));
      if (descriptionSet.isEmpty()) {
        current.add(descriptor);
        return true;
      }
      return false;
    }

    public void dispose() {
      super.dispose();
      myFilteredInstalled.clear();
      myFilteredAvailable.clear();
    }
  }
}
