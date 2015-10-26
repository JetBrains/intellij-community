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
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.options.BaseConfigurable;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.SplitterProportionsData;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.table.TableModel;
import java.util.List;

public class PluginManagerConfigurable extends BaseConfigurable implements SearchableConfigurable, Configurable.NoScroll {
  public static final String ID = "preferences.pluginManager";
  public static final String DISPLAY_NAME = IdeBundle.message("title.plugins");

  protected final PluginManagerUISettings myUISettings;

  private PluginManagerMain myPluginManagerMain;
  private boolean myAvailable;
  private boolean myShutdownRequired;

  public PluginManagerConfigurable(final PluginManagerUISettings UISettings) {
    myUISettings = UISettings;
  }
  
  public PluginManagerConfigurable(final PluginManagerUISettings UISettings, boolean available) {
    myUISettings = UISettings;
    myAvailable = available;
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myPluginManagerMain == null ? null : myPluginManagerMain.getPluginTable();
  }

  @Override
  public String getDisplayName() {
    return DISPLAY_NAME;
  }

  @Override
  public void reset() {
    myPluginManagerMain.reset();
    myPluginManagerMain.pluginsModel.sort();
    getSplitterProportions().restoreSplitterProportions(myPluginManagerMain.getMainPanel());
  }

  @Override
  @NotNull
  public String getHelpTopic() {
    return ID;
  }

  @Override
  public void disposeUIResources() {
    if (myPluginManagerMain != null) {
      getSplitterProportions().saveSplitterProportions(myPluginManagerMain.getMainPanel());

      if (myAvailable) {
        final RowSorter<? extends TableModel> rowSorter = myPluginManagerMain.pluginTable.getRowSorter();
        if (rowSorter != null) {
          final List<? extends RowSorter.SortKey> sortKeys = rowSorter.getSortKeys();
          if (sortKeys.size() > 0) {
            final RowSorter.SortKey sortKey = sortKeys.get(0);
            myUISettings.AVAILABLE_SORT_COLUMN_ORDER = sortKey.getSortOrder().ordinal();
          }
        }
        myUISettings.availableSortByStatus = myPluginManagerMain.pluginsModel.isSortByStatus();
      }
      else {
        myUISettings.installedSortByStatus = myPluginManagerMain.pluginsModel.isSortByStatus();
      }

      Disposer.dispose(myPluginManagerMain);
      myPluginManagerMain = null;
    }
  }

  private SplitterProportionsData getSplitterProportions() {
    return myAvailable ? myUISettings.availableProportions : myUISettings.installedProportions;
  }

  @Override
  public JComponent createComponent() {
    return getOrCreatePanel().getMainPanel();
  }

  protected PluginManagerMain createPanel() {
    return new InstalledPluginsManagerMain(myUISettings);
  }

  @Override
  public void apply() throws ConfigurationException {
    final String applyMessage = myPluginManagerMain.apply();
    if (applyMessage != null) {
      throw new ConfigurationException(applyMessage);
    }
    boolean prev = myShutdownRequired;
    myShutdownRequired |= myPluginManagerMain.isRequireShutdown();
    myPluginManagerMain.ignoreChanges();
    if (prev) return;

    Disposable d = UIUtil.getParents(myPluginManagerMain.getMainPanel()).filter(Disposable.class).first();
    if (d == null) return;
    Disposer.register(d, new Disposable() {
      @Override
      public void dispose() {
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          @Override
          public void run() {
            showShutdownDialogIfNeeded();
          }
        }, ApplicationManager.getApplication().getDisposed());
      }
    });
  }

  private void showShutdownDialogIfNeeded() {
    if (!myShutdownRequired) return;

    if (showRestartDialog() == Messages.YES) {
      ApplicationManagerEx.getApplicationEx().restart(true);
    }
  }

  public PluginManagerMain getOrCreatePanel() {
    if (myPluginManagerMain == null) {
      myPluginManagerMain = createPanel();
    }
    return myPluginManagerMain;
  }

  @Messages.YesNoResult
  public static int showRestartDialog() {
    return showRestartDialog(IdeBundle.message("update.notifications.title"));
  }

  @Messages.YesNoResult
  public static int showRestartDialog(@NotNull String title) {
    String action = IdeBundle.message(ApplicationManagerEx.getApplicationEx().isRestartCapable() ? "ide.restart.action" : "ide.shutdown.action");
    String message = IdeBundle.message("ide.restart.required.message", action, ApplicationNamesInfo.getInstance().getFullProductName());
    return Messages.showYesNoDialog(message, title, action, IdeBundle.message("ide.postpone.action"), Messages.getQuestionIcon());
  }

  public static void shutdownOrRestartApp() {
    shutdownOrRestartApp(IdeBundle.message("update.notifications.title"));
  }

  public static void shutdownOrRestartApp(@NotNull String title) {
    if (showRestartDialog(title) == Messages.YES) {
      ApplicationManagerEx.getApplicationEx().restart(true);
    }
  }

  @Override
  public boolean isModified() {
    return myPluginManagerMain != null && myPluginManagerMain.isModified();
  }

  @Override
  @NotNull
  public String getId() {
    return getHelpTopic();
  }

  @Override
  @Nullable
  public Runnable enableSearch(final String option) {
    return new Runnable() {
      @Override
      public void run() {
        if (myPluginManagerMain != null) {
          myPluginManagerMain.filter(option);
        }
      }
    };
  }

  public void select(IdeaPluginDescriptor... descriptors) {
    myPluginManagerMain.select(descriptors);
  }
}
