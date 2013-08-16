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
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.SplitterProportionsData;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.table.TableModel;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: stathik
 * Date: Oct 26, 2003
 * Time: 9:30:44 PM
 * To change this template use Options | File Templates.
 */
public class PluginManagerConfigurable extends BaseConfigurable implements SearchableConfigurable, Configurable.NoScroll {

  @NonNls private static final String POSTPONE = "&Postpone";
  public static final String ID = "preferences.pluginManager";
  public static final String DISPLAY_NAME = IdeBundle.message("title.plugins");
  public boolean EXPANDED = false;
  public String FIND = "";
  public boolean TREE_VIEW = false;

  private PluginManagerMain myPluginManagerMain;
  protected final PluginManagerUISettings myUISettings;
  protected boolean myAvailable;

  public PluginManagerConfigurable(final PluginManagerUISettings UISettings) {
    myUISettings = UISettings;
  }
  
  public PluginManagerConfigurable(final PluginManagerUISettings UISettings, boolean available) {
    myUISettings = UISettings;
    myAvailable = available;
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myPluginManagerMain.getPluginTable();
  }

  public String getDisplayName() {
    return DISPLAY_NAME;
  }

  public void reset() {
    myPluginManagerMain.reset();
    if (myAvailable) {
      final int column = myUISettings.AVAILABLE_SORT_MODE;
      if (column >= 0) {
        for (final SortOrder sortOrder : SortOrder.values()) {
          if (sortOrder.ordinal() == myUISettings.AVAILABLE_SORT_COLUMN_ORDER) {
            myPluginManagerMain.pluginsModel.setSortKey(new RowSorter.SortKey(column, sortOrder));
            break;
          }
        }
      }
      myPluginManagerMain.pluginsModel.setSortByStatus(myUISettings.AVAILABLE_SORT_BY_STATUS);
    } else {
      myPluginManagerMain.pluginsModel.setSortByStatus(myUISettings.INSTALLED_SORT_BY_STATUS);
    }
    myPluginManagerMain.pluginsModel.sort();
    getSplitterProportions().restoreSplitterProportions(myPluginManagerMain.getMainPanel());
  }

  public String getHelpTopic() {
    return ID;
  }

  public void disposeUIResources() {
    if (myPluginManagerMain != null) {
      getSplitterProportions().saveSplitterProportions(myPluginManagerMain.getMainPanel());

      if (myAvailable) {
        final RowSorter<? extends TableModel> rowSorter = myPluginManagerMain.pluginTable.getRowSorter();
        if (rowSorter != null) {
          final List<? extends RowSorter.SortKey> sortKeys = rowSorter.getSortKeys();
          if (sortKeys.size() > 0) {
            final RowSorter.SortKey sortKey = sortKeys.get(0);
            myUISettings.AVAILABLE_SORT_MODE = sortKey.getColumn();
            myUISettings.AVAILABLE_SORT_COLUMN_ORDER = sortKey.getSortOrder().ordinal();
          }
        }
        myUISettings.AVAILABLE_SORT_BY_STATUS = myPluginManagerMain.pluginsModel.isSortByStatus();
      } else {
        myUISettings.INSTALLED_SORT_BY_STATUS = myPluginManagerMain.pluginsModel.isSortByStatus();
      }

      Disposer.dispose(myPluginManagerMain);
      myPluginManagerMain = null;
    }
  }

  private SplitterProportionsData getSplitterProportions() {
    return myAvailable ? myUISettings.getAvailableSplitterProportionsData() : myUISettings.getSplitterProportionsData();
  }

  public JComponent createComponent() {
    return getOrCreatePanel().getMainPanel();
  }

  protected PluginManagerMain createPanel() {
    return new InstalledPluginsManagerMain(myUISettings);
  }

  public void apply() throws ConfigurationException {
    final String applyMessage = myPluginManagerMain.apply();
    if (applyMessage != null) {
      throw new ConfigurationException(applyMessage);
    }

    if (myPluginManagerMain.isRequireShutdown()) {
      final ApplicationEx app = ApplicationManagerEx.getApplicationEx();

      int response = app.isRestartCapable() ? showRestartIDEADialog() : showShutDownIDEADialog();
      if (response == 0) {
        app.restart(true);
      }
      else {
        myPluginManagerMain.ignoreChanges();
      }
    }
  }

  public PluginManagerMain getOrCreatePanel() {
    if (myPluginManagerMain == null) {
      myPluginManagerMain = createPanel();
    }
    return myPluginManagerMain;
  }

  public static int showShutDownIDEADialog() {
    return showShutDownIDEADialog(IdeBundle.message("title.plugins.changed"));
  }

  public static int showShutDownIDEADialog(final String title) {
    String message = IdeBundle.message("message.idea.shutdown.required", ApplicationNamesInfo.getInstance().getFullProductName());
    return Messages.showYesNoDialog(message, title, "Shut Down", POSTPONE, Messages.getQuestionIcon());
  }

  public static int showRestartIDEADialog() {
    return showRestartIDEADialog(IdeBundle.message("title.plugins.changed"));
  }

  public static int showRestartIDEADialog(final String title) {
    String message = IdeBundle.message("message.idea.restart.required", ApplicationNamesInfo.getInstance().getFullProductName());
    return Messages.showYesNoDialog(message, title, "Restart", POSTPONE, Messages.getQuestionIcon());
  }

  public static void shutdownOrRestartApp(String title) {
    final ApplicationEx app = ApplicationManagerEx.getApplicationEx();
    int response = app.isRestartCapable() ? showRestartIDEADialog(title) : showShutDownIDEADialog(title);
    if (response == 0) app.restart(true);
  }

  public boolean isModified() {
    return myPluginManagerMain != null && myPluginManagerMain.isModified();
  }

  @NotNull
  public String getId() {
    return getHelpTopic();
  }

  @Nullable
  public Runnable enableSearch(final String option) {
    return new Runnable(){
      public void run() {
        if (myPluginManagerMain == null) return;
        myPluginManagerMain.filter(option);
      }
    };
  }

  public void select(IdeaPluginDescriptor... descriptors) {
    myPluginManagerMain.select(descriptors);
  }
}
