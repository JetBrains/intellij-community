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

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.ex.ComboBoxAction;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.updateSettings.impl.UpdateSettings;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.util.net.HTTPProxySettingsDialog;
import com.intellij.util.ui.update.UiNotifyConnector;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.TreeSet;

/**
 * User: anna
 */
public class AvailablePluginsManagerMain extends PluginManagerMain {
  public static final String MANAGE_REPOSITORIES = "Manage repositories...";
  public static final String N_A = "N/A";

  private PluginManagerMain installed;

  public AvailablePluginsManagerMain(PluginManagerMain installed, PluginManagerUISettings uiSettings) {
    super(uiSettings);
    this.installed = installed;
    init();
    final JButton manageRepositoriesBtn = new JButton(MANAGE_REPOSITORIES);
    manageRepositoriesBtn.setMnemonic('m');
    manageRepositoriesBtn.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        if (ShowSettingsUtil.getInstance().editConfigurable(myActionsPanel, new PluginHostsConfigurable())) {
          final ArrayList<String> pluginHosts = UpdateSettings.getInstance().myPluginHosts;
          if (!pluginHosts.contains(((AvailablePluginsTableModel)pluginsModel).getRepository())) {
            ((AvailablePluginsTableModel)pluginsModel).setRepository(AvailablePluginsTableModel.ALL, myFilter.getFilter().toLowerCase());
          }
          loadAvailablePlugins();
        }
      }
    });
    myActionsPanel.add(manageRepositoriesBtn, BorderLayout.EAST);

    final JButton httpProxySettingsButton = new JButton(IdeBundle.message("button.http.proxy.settings"));
    httpProxySettingsButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        HTTPProxySettingsDialog settingsDialog = new HTTPProxySettingsDialog();
        settingsDialog.pack();
        settingsDialog.show();
        if (settingsDialog.isOK()) {
          loadAvailablePlugins();
        }
      }
    });
    myActionsPanel.add(httpProxySettingsButton, BorderLayout.WEST);
  }

  @Override
  protected JScrollPane createTable() {
    pluginsModel = new AvailablePluginsTableModel();
    pluginTable = new PluginTable(pluginsModel);
    pluginTable.getTableHeader().setReorderingAllowed(false);
    pluginTable.setColumnWidth(PluginManagerColumnInfo.COLUMN_DOWNLOADS, 60);
    pluginTable.setColumnWidth(PluginManagerColumnInfo.COLUMN_DATE, 60);
    pluginTable.setColumnWidth(PluginManagerColumnInfo.COLUMN_RATE, 70);

    return ScrollPaneFactory.createScrollPane(pluginTable);
  }

  @Override
  protected void installTableActions(final PluginTable pluginTable) {
    super.installTableActions(pluginTable);
    pluginTable.addMouseListener(new MouseAdapter() {
      public void mouseClicked(MouseEvent e) {
        if (e.getClickCount() != 2) return;
        if (pluginTable.columnAtPoint(e.getPoint()) < 0) return;
        if (pluginTable.rowAtPoint(e.getPoint()) < 0) return;
        IdeaPluginDescriptor[] selection = pluginTable.getSelectedObjects();
        if (selection != null) {
          boolean enabled = true;
          for (IdeaPluginDescriptor descr : selection) {
            if (descr instanceof PluginNode) {
              enabled &= !PluginManagerColumnInfo.isDownloaded((PluginNode)descr);
              if (((PluginNode)descr).getStatus() == PluginNode.STATUS_INSTALLED) {
                enabled &= InstalledPluginsTableModel.hasNewerVersion(descr.getPluginId());
              }
            }
            else if (descr instanceof IdeaPluginDescriptorImpl) {
              PluginId id = descr.getPluginId();
              enabled &= InstalledPluginsTableModel.hasNewerVersion(id);
            }
          }
          if (enabled) {
            new ActionInstallPlugin(AvailablePluginsManagerMain.this, installed).install();
          }
        }
      }
    });
  }

  @Override
  public void reset() {
    UiNotifyConnector.doWhenFirstShown(getPluginTable(), new Runnable() {
      @Override
      public void run() {
        loadAvailablePlugins();
      }
    });
    super.reset();
  }

  @Override
  protected ActionGroup getActionGroup(boolean inToolbar) {
    DefaultActionGroup actionGroup = new DefaultActionGroup();
    actionGroup.add(new RefreshAction());
    actionGroup.add(new ActionInstallPlugin(this, installed));
    if (inToolbar) {
      actionGroup.add(new SortByStatusAction("Sort installed first"));
      actionGroup.add(new MyFilterRepositoryAction());
      actionGroup.add(new MyFilterCategoryAction());
    }
    return actionGroup;
  }

  @Override
  protected void propagateUpdates(ArrayList<IdeaPluginDescriptor> list) {
    installed.modifyPluginsList(list); //propagate updates
  }

  private class MyFilterCategoryAction extends ComboBoxAction implements DumbAware{
    @Override
    public void update(AnActionEvent e) {
      super.update(e);
      String category = ((AvailablePluginsTableModel)pluginsModel).getCategory();
      if (category == null) {
        category = N_A;
      }
      e.getPresentation().setText("Category: " + category);
    }

    @NotNull
    @Override
    protected DefaultActionGroup createPopupActionGroup(JComponent button) {
      final TreeSet<String> availableCategories = ((AvailablePluginsTableModel)pluginsModel).getAvailableCategories();
      final DefaultActionGroup gr = new DefaultActionGroup();
      gr.add(createFilterByCategoryAction(AvailablePluginsTableModel.ALL));
      final boolean noCategory = availableCategories.remove(N_A);
      for (final String availableCategory : availableCategories) {
        gr.add(createFilterByCategoryAction(availableCategory));
      }
      if (noCategory) {
        gr.add(createFilterByCategoryAction(N_A));
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

  private class MyFilterRepositoryAction extends ComboBoxAction implements DumbAware {

    private static final int LENGTH = 15;

    @Override
    public void update(AnActionEvent e) {
      super.update(e);
      e.getPresentation().setVisible(!UpdateSettings.getInstance().myPluginHosts.isEmpty());
      String repository = ((AvailablePluginsTableModel)pluginsModel).getRepository();
      if (repository.length() > LENGTH) {
        repository = repository.substring(0, LENGTH) + "...";
      }
      e.getPresentation().setText("Repository: " + repository);
    }

    @NotNull
    @Override
    protected DefaultActionGroup createPopupActionGroup(JComponent button) {
      final DefaultActionGroup gr = new DefaultActionGroup();
      gr.add(createFilterByRepositoryAction(AvailablePluginsTableModel.ALL));
      gr.add(createFilterByRepositoryAction(AvailablePluginsTableModel.JETBRAINS_REPO));
      for (final String host : UpdateSettings.getInstance().myPluginHosts) {
        gr.add(createFilterByRepositoryAction(host));
      }
      return gr;
    }

    private AnAction createFilterByRepositoryAction(final String host) {
      return new AnAction(host) {
        @Override
        public void actionPerformed(AnActionEvent e) {
          final String filter = myFilter.getFilter().toLowerCase();
          ((AvailablePluginsTableModel)pluginsModel).setRepository(host, filter);
        }
      };
    }
  }
}
