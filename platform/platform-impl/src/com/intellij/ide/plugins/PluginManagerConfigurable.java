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
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.IconLoader;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

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
    myUISettings.getAvailableTableProportions().restoreProportion(myPluginManagerMain.getAvailablePluginsTable());
    myUISettings.getInstalledTableProportions().restoreProportion(myPluginManagerMain.getInstalledPluginTable());
  }

  public String getHelpTopic() {
    return "preferences.pluginManager";
  }

  public void disposeUIResources() {
    if (myPluginManagerMain != null) {
      myUISettings.getSplitterProportionsData().saveSplitterProportions(myPluginManagerMain.getMainPanel());
      myUISettings.getAvailableTableProportions().saveProportion(myPluginManagerMain.getAvailablePluginsTable());
      myUISettings.getInstalledTableProportions().saveProportion(myPluginManagerMain.getInstalledPluginTable());
      Disposer.dispose(myPluginManagerMain);
      myPluginManagerMain = null;
    }
  }

  public JComponent createComponent() {
    if (myPluginManagerMain == null) {
      myPluginManagerMain = new PluginManagerMain( new MyInstalledProvider() , new MyAvailableProvider());
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

  private class MyInstalledProvider implements SortableProvider {
    public int getSortOrder() {
      return myUISettings.INSTALLED_SORT_COLUMN_ORDER;
    }

    public int getSortColumn() {
      return myUISettings.INSTALLED_SORT_COLUMN;
    }

    public void setSortOrder(int sortOrder) {
      myUISettings.INSTALLED_SORT_COLUMN_ORDER = sortOrder;
    }

    public void setSortColumn(int sortColumn) {
      myUISettings.INSTALLED_SORT_COLUMN = sortColumn;
    }
  }

  private class MyAvailableProvider implements SortableProvider {
    public int getSortOrder() {
      return myUISettings.AVAILABLE_SORT_COLUMN_ORDER;
    }

    public int getSortColumn() {
      return myUISettings.AVAILABLE_SORT_COLUMN;
    }

    public void setSortOrder(int sortOrder) {
      myUISettings.AVAILABLE_SORT_COLUMN_ORDER = sortOrder;
    }

    public void setSortColumn(int sortColumn) {
      myUISettings.AVAILABLE_SORT_COLUMN = sortColumn;
    }
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
