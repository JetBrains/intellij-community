/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import com.intellij.ide.plugins.sorters.SortByDownloadsAction;
import com.intellij.ide.plugins.sorters.SortByRatingAction;
import com.intellij.ide.plugins.sorters.SortByUpdatedAction;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ComboBoxAction;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.updateSettings.impl.UpdateSettings;
import com.intellij.ui.DoubleClickListener;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.TableUtil;
import com.intellij.util.net.HttpConfigurable;
import com.intellij.util.ui.update.UiNotifyConnector;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;

/**
 * User: anna
 */
public class AvailablePluginsManagerMain extends PluginManagerMain {
  public static final String MANAGE_REPOSITORIES = "Manage repositories...";
  public static final String N_A = "N/A";

  private static final InstalledPluginsState ourState = InstalledPluginsState.getInstance();

  private final PluginManagerMain installed;
  private final String myVendorFilter;

  public AvailablePluginsManagerMain(PluginManagerMain installed, PluginManagerUISettings uiSettings, String vendorFilter) {
    super(uiSettings);
    this.installed = installed;
    myVendorFilter = vendorFilter;
    init();
    final JButton manageRepositoriesBtn = new JButton(MANAGE_REPOSITORIES);
    if (myVendorFilter == null) {
      manageRepositoriesBtn.setMnemonic('m');
      manageRepositoriesBtn.addActionListener(new ActionListener() {
        @Override
        public void actionPerformed(@NotNull ActionEvent e) {
          if (ShowSettingsUtil.getInstance().editConfigurable(myActionsPanel, new PluginHostsConfigurable())) {
            final List<String> pluginHosts = UpdateSettings.getInstance().getPluginHosts();
            if (!pluginHosts.contains(((AvailablePluginsTableModel)pluginsModel).getRepository())) {
              ((AvailablePluginsTableModel)pluginsModel).setRepository(AvailablePluginsTableModel.ALL, myFilter.getFilter().toLowerCase(Locale.ENGLISH));
            }
            loadAvailablePlugins();
          }
        }
      });
      myActionsPanel.add(manageRepositoriesBtn, BorderLayout.EAST);
    }

    final JButton httpProxySettingsButton = new JButton(IdeBundle.message("button.http.proxy.settings"));
    httpProxySettingsButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(@NotNull ActionEvent e) {
        if (HttpConfigurable.editConfigurable(getMainPanel())) {
          loadAvailablePlugins();
        }
      }
    });
    myActionsPanel.add(httpProxySettingsButton, BorderLayout.WEST);
    myPanelDescription.setVisible(false);
  }

  @Override
  protected JScrollPane createTable() {
    AvailablePluginsTableModel model = new AvailablePluginsTableModel();
    model.setVendor(myVendorFilter);
    if (PluginManagerUISettings.getInstance().availableSortByStatus) {
      model.setSortByStatus(true);
    }

    pluginsModel = model;
    pluginTable = new PluginTable(pluginsModel);
    return ScrollPaneFactory.createScrollPane(pluginTable);
  }

  @Override
  protected void installTableActions() {
    super.installTableActions();
    new DoubleClickListener() {
      @Override
      protected boolean onDoubleClick(MouseEvent e) {
        if (pluginTable.columnAtPoint(e.getPoint()) < 0) return false;
        if (pluginTable.rowAtPoint(e.getPoint()) < 0) return false;
        return installSelected(pluginTable);
      }
    }.installOn(pluginTable);
    
    pluginTable.registerKeyboardAction(
      new ActionListener() {
        @Override
        public void actionPerformed(@NotNull ActionEvent e) {
          installSelected(pluginTable);
        }
      },
      KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0),
      JComponent.WHEN_FOCUSED
    );
  }

  private boolean installSelected(PluginTable pluginTable) {
    IdeaPluginDescriptor[] selection = pluginTable.getSelectedObjects();
    if (selection != null) {
      boolean enabled = true;
      for (IdeaPluginDescriptor descr : selection) {
        if (descr instanceof PluginNode) {
          enabled &= !PluginManagerColumnInfo.isDownloaded((PluginNode)descr);
          if (((PluginNode)descr).getStatus() == PluginNode.STATUS_INSTALLED) {
            enabled &= ourState.hasNewerVersion(descr.getPluginId());
          }
        }
        else if (descr instanceof IdeaPluginDescriptorImpl) {
          PluginId id = descr.getPluginId();
          enabled &= ourState.hasNewerVersion(id);
        }
      }
      if (enabled) {
        new InstallPluginAction(this, installed).install(null);
      }
      return true;
    }
    return false;
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
  protected PluginManagerMain getAvailable() {
    return this;
  }

  @Override
  protected PluginManagerMain getInstalled() {
    return installed;
  }

  @Override
  protected ActionGroup getActionGroup(boolean inToolbar) {
    DefaultActionGroup actionGroup = new DefaultActionGroup();
    actionGroup.add(new RefreshAction());

    if (inToolbar) {
      actionGroup.add(new MyFilterRepositoryAction());
      actionGroup.add(new MyFilterCategoryAction());
    }
    else {
      actionGroup.add(createSortersGroup());
      actionGroup.add(Separator.getInstance());
      actionGroup.add(new InstallPluginAction(getAvailable(), getInstalled()));
    }
    return actionGroup;
  }

  @Override
  protected boolean acceptHost(String host) {
    return ((AvailablePluginsTableModel)pluginsModel).isHostAccepted(host);
  }

  @Override
  protected void propagateUpdates(List<IdeaPluginDescriptor> list) {
    installed.modifyPluginsList(list); //propagate updates
  }

  @Override
  protected DefaultActionGroup createSortersGroup() {
    final DefaultActionGroup group = super.createSortersGroup();
    group.addAction(new SortByDownloadsAction(pluginTable, pluginsModel));
    group.addAction(new SortByRatingAction(pluginTable, pluginsModel));
    group.addAction(new SortByUpdatedAction(pluginTable, pluginsModel));
    return group;
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
      return new DumbAwareAction(availableCategory) {
        @Override
        public void actionPerformed(AnActionEvent e) {
          final String filter = myFilter.getFilter().toLowerCase(Locale.ENGLISH);
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
      boolean empty = UpdateSettings.getInstance().getPluginHosts().isEmpty();
      e.getPresentation().setVisible(!empty || ApplicationInfoEx.getInstanceEx().getBuiltinPluginsUrl() != null);
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
      if (ApplicationInfoEx.getInstanceEx().getBuiltinPluginsUrl() != null) {
        gr.add(createFilterByRepositoryAction(AvailablePluginsTableModel.BUILTIN_REPO));
      }

      for (final String host : UpdateSettings.getInstance().getPluginHosts()) {
        gr.add(createFilterByRepositoryAction(host));
      }
      return gr;
    }

    private AnAction createFilterByRepositoryAction(final String host) {
      return new DumbAwareAction(host) {
        @Override
        public void actionPerformed(AnActionEvent e) {
          final String filter = myFilter.getFilter().toLowerCase(Locale.ENGLISH);
          ((AvailablePluginsTableModel)pluginsModel).setRepository(host, filter);
          TableUtil.ensureSelectionExists(getPluginTable());
        }
      };
    }
  }
}
