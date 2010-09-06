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

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.ex.ApplicationEx;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.options.BaseConfigurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.IconLoader;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.table.TableModel;
import java.util.Arrays;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: stathik
 * Date: Oct 26, 2003
 * Time: 9:30:44 PM
 * To change this template use Options | File Templates.
 */
public class PluginManagerConfigurable extends BaseConfigurable implements SearchableConfigurable {

  public boolean EXPANDED = false;
  public String FIND = "";
  public boolean TREE_VIEW = false;

  private PluginManagerMain myPluginManagerMain;
  private final PluginManagerUISettings myUISettings;

  public PluginManagerConfigurable(final PluginManagerUISettings UISettings) {
    myUISettings = UISettings;
  }

  public String getDisplayName() {
    return IdeBundle.message("title.plugins");
  }

  public void reset() {
    myPluginManagerMain.reset();
    myUISettings.getSplitterProportionsData().restoreSplitterProportions(myPluginManagerMain.getMainPanel());

    final PluginTable availablePluginsTable = myPluginManagerMain.getAvailablePluginsTable();
    final PluginTable installedPluginTable = myPluginManagerMain.getInstalledPluginTable();

    myUISettings.getAvailableTableProportions().restoreProportion(availablePluginsTable);
    myUISettings.getInstalledTableProportions().restoreProportion(installedPluginTable);

    restoreSorting(availablePluginsTable, true);
    restoreSorting(installedPluginTable, false);
  }

  private void restoreSorting(final PluginTable table, final boolean available) {
    final RowSorter<? extends TableModel> rowSorter = table.getRowSorter();
    if (rowSorter != null) {
      final int column = available ? myUISettings.AVAILABLE_SORT_COLUMN : myUISettings.INSTALLED_SORT_COLUMN;
      if (column >= 0) {
        final int orderOrdinal = available ? myUISettings.AVAILABLE_SORT_COLUMN_ORDER : myUISettings.INSTALLED_SORT_COLUMN_ORDER;
        for (final SortOrder sortOrder : SortOrder.values()) {
          if (sortOrder.ordinal() == orderOrdinal) {
            rowSorter.setSortKeys(Arrays.asList(new RowSorter.SortKey(column, sortOrder)));
          }

        }
      }
    }
  }

  public String getHelpTopic() {
    return "preferences.pluginManager";
  }

  public void disposeUIResources() {
    if (myPluginManagerMain != null) {
      myUISettings.getSplitterProportionsData().saveSplitterProportions(myPluginManagerMain.getMainPanel());
      final PluginTable availablePluginsTable = myPluginManagerMain.getAvailablePluginsTable();
      final PluginTable installedPluginTable = myPluginManagerMain.getInstalledPluginTable();
      myUISettings.getAvailableTableProportions().saveProportion(availablePluginsTable);
      myUISettings.getInstalledTableProportions().saveProportion(installedPluginTable);

      saveSorting(availablePluginsTable, true);
      saveSorting(installedPluginTable, false);

      Disposer.dispose(myPluginManagerMain);
      myPluginManagerMain = null;
    }
  }

  private void saveSorting(final PluginTable availablePluginsTable, final boolean available) {
    final RowSorter<? extends TableModel> rowSorter = availablePluginsTable.getRowSorter();
    if (rowSorter != null) {
      final List<? extends RowSorter.SortKey> sortKeys = rowSorter.getSortKeys();
      if (sortKeys.size() > 0) {
        final RowSorter.SortKey sortKey = sortKeys.get(0);
        if (available) {
          myUISettings.AVAILABLE_SORT_COLUMN = sortKey.getColumn();
          myUISettings.AVAILABLE_SORT_COLUMN_ORDER = sortKey.getSortOrder().ordinal();
        }
        else {
          myUISettings.INSTALLED_SORT_COLUMN = sortKey.getColumn();
          myUISettings.INSTALLED_SORT_COLUMN_ORDER = sortKey.getSortOrder().ordinal();

        }
      }
    }
  }

  public JComponent createComponent() {
    if (myPluginManagerMain == null) {
      myPluginManagerMain = new PluginManagerMain( );

      final PluginTable availablePluginsTable = myPluginManagerMain.getAvailablePluginsTable();
      final PluginTable installedPluginTable = myPluginManagerMain.getInstalledPluginTable();
      restoreSorting(availablePluginsTable, true);
      restoreSorting(installedPluginTable, false);
    }

    return myPluginManagerMain.getMainPanel();
  }

  public void apply() throws ConfigurationException {
    myPluginManagerMain.apply();
    if (myPluginManagerMain.isRequireShutdown()) {
      final ApplicationEx app = ApplicationManagerEx.getApplicationEx();
      if (app.isRestartCapable()) {
        if (Messages.showYesNoDialog(IdeBundle.message("message.idea.restart.required", ApplicationNamesInfo.getInstance().getProductName()),
                                     IdeBundle.message("title.plugins"), Messages.getQuestionIcon()) == 0) {
          app.restart();
        }
        else {
          myPluginManagerMain.ignoreChanges();
        }
      }
      else {
        if (Messages.showYesNoDialog(IdeBundle.message("message.idea.shutdown.required", ApplicationNamesInfo.getInstance().getProductName()),
                                     IdeBundle.message("title.plugins"), Messages.getQuestionIcon()) == 0) {
          app.exit(true);
        }
        else {
          myPluginManagerMain.ignoreChanges();
        }
      }
    }
  }

  public boolean isModified() {
    return myPluginManagerMain != null && myPluginManagerMain.isModified();
  }

  public Icon getIcon() {
    return IconLoader.getIcon("/general/pluginManager.png");
  }

  public String getId() {
    return getHelpTopic();
  }

  @Nullable
  public Runnable enableSearch(final String option) {
    return new Runnable(){
      public void run() {
        myPluginManagerMain.filter(option);
      }
    };
  }

  public void select(IdeaPluginDescriptor... descriptors) {
    myPluginManagerMain.select(descriptors);
  }
}
