/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ComboBoxAction;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.IconLoader;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.util.net.HTTPProxySettingsDialog;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.update.UiNotifyConnector;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashSet;

/**
 * User: anna
 */
public class AvailablePluginsManagerMain extends PluginManagerMain {
  private PluginManagerMain installed;
  private PluginManagerUISettings myUISettings;
  private JButton myHttpProxySettingsButton = new JButton(IdeBundle.message("button.http.proxy.settings"));

  public AvailablePluginsManagerMain(PluginManagerMain installed, PluginManagerUISettings uiSettings) {
    this.installed = installed;
    myUISettings = uiSettings;
    init();
  }

  @Override
  protected JScrollPane createTable() {
    pluginsModel = new AvailablePluginsTableModel();
    pluginTable = new PluginTable(pluginsModel);
    JScrollPane availableScrollPane = ScrollPaneFactory.createScrollPane(pluginTable);
    myActionsPanel.add(myHttpProxySettingsButton, BorderLayout.NORTH);
    myHttpProxySettingsButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        HTTPProxySettingsDialog settingsDialog = new HTTPProxySettingsDialog();
        settingsDialog.pack();
        settingsDialog.show();
        if (settingsDialog.isOK()) {
          loadAvailablePlugins();
        }
      }
    });

    return availableScrollPane;
  }

  @Override
  public void reset() {
    super.reset();

    UiNotifyConnector.doWhenFirstShown(getPluginTable(), new Runnable() {
      @Override
      public void run() {
        loadAvailablePlugins();
      }
    });
  }

  @Override
  protected ActionGroup getActionGroup(boolean inToolbar) {
    DefaultActionGroup actionGroup = new DefaultActionGroup();
    if (!inToolbar) {
      actionGroup.add(new ActionInstallPlugin(this, installed));
    }
    actionGroup.add(new AnAction("Reload list of plugins", "Reload list of plugins", IconLoader.getIcon("/vcs/refresh.png")) {
      @Override
      public void actionPerformed(AnActionEvent e) {
        loadAvailablePlugins();
        myFilter.setFilter("");
      }
    });
    if (inToolbar) {
      actionGroup.add(new MyFilterCategoryAction());
      actionGroup.add(new SortByNameAction());
    }
    return actionGroup;
  }

  /**
   * Start a new thread which downloads new list of plugins from the site in
   * the background and updates a list of plugins in the table.
   */
  private void loadPluginsFromHostInBackground() {
    setDownloadStatus(true);

    new com.intellij.util.concurrency.SwingWorker() {
      ArrayList<IdeaPluginDescriptor> list = null;
      Exception error;

      public Object construct() {
        try {
          list = RepositoryHelper.process(null);
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
              pluginsModel.setSortMode(myUISettings.AVAILABLE_SORT_MODE);
            }
            else if (error != null) {
              LOG.info(error);
              setDownloadStatus(false);
              if (0 == Messages.showOkCancelDialog(
                IdeBundle.message("error.list.of.plugins.was.not.loaded", error.getMessage()),
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

  private void setDownloadStatus(boolean status) {
    pluginTable.setPaintBusy(status);
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

  private class MyFilterCategoryAction extends ComboBoxAction {
    @Override
    public void update(AnActionEvent e) {
      super.update(e);
      e.getPresentation().setText("Category: " + ((AvailablePluginsTableModel)pluginsModel).getCategory());
    }

    @NotNull
    @Override
    protected DefaultActionGroup createPopupActionGroup(JComponent button) {
      final LinkedHashSet<String> availableCategories = ((AvailablePluginsTableModel)pluginsModel).getAvailableCategories();
      final DefaultActionGroup gr = new DefaultActionGroup();
      gr.add(createFilterByCategoryAction(AvailablePluginsTableModel.ALL));
      for (final String availableCategory : availableCategories) {
        gr.add(createFilterByCategoryAction(availableCategory));
      }
      return gr;
    }

    private AnAction createFilterByCategoryAction(final String availableCategory) {
      return new AnAction(availableCategory) {
        @Override
        public void actionPerformed(AnActionEvent e) {
          final String filter = myFilter.getFilter().toLowerCase();
          ((AvailablePluginsTableModel)pluginsModel).setCategory(availableCategory, filter);
        }
      };
    }
  }

  private class SortByNameAction extends ComboBoxAction {

    @Override
    public void update(AnActionEvent e) {
      super.update(e);
      e.getPresentation().setText("Sort by: " + pluginsModel.getSortMode());
    }

    @NotNull
    @Override
    protected DefaultActionGroup createPopupActionGroup(JComponent button) {
      final DefaultActionGroup gr = new DefaultActionGroup();
      for (final String sortMode : AvailablePluginsTableModel.SORT_MODES) {
        gr.add(new AnAction(sortMode) {
          @Override
          public void actionPerformed(AnActionEvent e) {
            pluginsModel.setSortMode(sortMode);
          }
        });
      }
      return gr;
    }
  }
}
