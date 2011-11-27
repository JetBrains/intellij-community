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
import com.intellij.openapi.util.IconLoader;
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

  public String getDisplayName() {
    return IdeBundle.message("title.plugins");
  }

  public void reset() {
    myPluginManagerMain.reset();
    if (myAvailable) {
      final int column = myUISettings.AVAILABLE_SORT_MODE;
      if (column >= 0) {
        myPluginManagerMain.pluginsModel.setSortKey(new RowSorter.SortKey(column, SortOrder.ASCENDING));
      }
      if (myUISettings.AVAILABLE_SORT_BY_STATUS) {
        myPluginManagerMain.pluginsModel.setSortByStatus(true);
      }
      myPluginManagerMain.pluginsModel.sort();
    }
    getSplitterProportions().restoreSplitterProportions(myPluginManagerMain.getMainPanel());
  }

  public String getHelpTopic() {
    return "preferences.pluginManager";
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
          }
        }
        myUISettings.AVAILABLE_SORT_BY_STATUS = myPluginManagerMain.pluginsModel.isSortByStatus();
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
      if (app.isRestartCapable()) {
        if (showRestartIDEADialog() == 0) {
          app.restart();
        }
        else {
          myPluginManagerMain.ignoreChanges();
        }
      }
      else {
        if (showShutDownIDEADialog() == 0) {
          app.exit(true);
        }
        else {
          myPluginManagerMain.ignoreChanges();
        }
      }
    }
  }

  public PluginManagerMain getOrCreatePanel() {
    if (myPluginManagerMain == null) {
      myPluginManagerMain = createPanel();
    }
    return myPluginManagerMain;
  }

  private static int showShutDownIDEADialog() {
    String message = IdeBundle.message("message.idea.shutdown.required", ApplicationNamesInfo.getInstance().getProductName());
    String title = IdeBundle.message("title.plugins.changed");
    return Messages.showYesNoDialog(message, title, "Shut Down", POSTPONE,Messages.getQuestionIcon());
  }

  public static int showRestartIDEADialog() {
    String message = IdeBundle.message("message.idea.restart.required", ApplicationNamesInfo.getInstance().getProductName());
    String title = IdeBundle.message("title.plugins.changed");
    return Messages.showYesNoDialog(message, title, "Restart", POSTPONE, Messages.getQuestionIcon());
  }

  public boolean isModified() {
    return myPluginManagerMain != null && myPluginManagerMain.isModified();
  }

  public Icon getIcon() {
    return IconLoader.getIcon("/general/pluginManager.png");
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
