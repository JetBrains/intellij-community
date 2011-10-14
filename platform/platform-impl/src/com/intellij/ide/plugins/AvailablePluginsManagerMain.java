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
import com.intellij.openapi.project.DumbAware;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.util.net.HTTPProxySettingsDialog;
import com.intellij.util.ui.update.UiNotifyConnector;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.LinkedHashSet;

/**
 * User: anna
 */
public class AvailablePluginsManagerMain extends PluginManagerMain {
  private PluginManagerMain installed;
  private JButton myHttpProxySettingsButton = new JButton(IdeBundle.message("button.http.proxy.settings"));

  public AvailablePluginsManagerMain(PluginManagerMain installed, PluginManagerUISettings uiSettings) {
    super(uiSettings);
    this.installed = installed;
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
    actionGroup.add(new RefreshAction());
    if (inToolbar) {
      actionGroup.add(new MyFilterCategoryAction());
      actionGroup.add(new SortByNameAction());
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

  private class SortByNameAction extends ComboBoxAction implements DumbAware{

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
