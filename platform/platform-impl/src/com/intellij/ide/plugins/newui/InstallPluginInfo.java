// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins.newui;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManagerConfigurable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.TaskInfo;
import com.intellij.openapi.wm.ex.StatusBarEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Alexander Lobas
 */
public class InstallPluginInfo {
  public final BgProgressIndicator indicator = new BgProgressIndicator();
  private final IdeaPluginDescriptor myDescriptor;
  private MyPluginModel myPluginModel;
  public final boolean install;
  public final IdeaPluginDescriptor updateDescriptor;
  private TaskInfo myStatusBarTaskInfo;

  public InstallPluginInfo(@NotNull IdeaPluginDescriptor descriptor,
                           IdeaPluginDescriptor updateDescriptor,
                           @NotNull MyPluginModel pluginModel,
                           boolean install) {
    myDescriptor = descriptor;
    this.updateDescriptor = updateDescriptor;
    myPluginModel = pluginModel;
    this.install = install;
  }

  public synchronized void toBackground(@Nullable StatusBarEx statusBar) {
    myPluginModel = null;
    indicator.removeStateDelegate(null);
    if (statusBar != null) {
      String title = (install ? "Installing plugin " : "Update plugin ") + myDescriptor.getName();
      statusBar.addProgress(indicator, myStatusBarTaskInfo = OneLineProgressIndicator.task(title));
    }
  }

  public synchronized void fromBackground(@NotNull MyPluginModel pluginModel) {
    myPluginModel = pluginModel;
    closeStatusBarIndicator();
  }

  public synchronized void finish(boolean success, boolean cancel) {
    if (myPluginModel == null) {
      MyPluginModel.finishInstall(myDescriptor);
      closeStatusBarIndicator();
      if (success) {
        ApplicationManager.getApplication()
          .invokeLater(() -> PluginManagerConfigurable.shutdownOrRestartApp(IdeBundle.message("update.notifications.title")));
      }
    }
    else if (!cancel) {
      myPluginModel.finishInstall(myDescriptor, success, true);
    }
  }

  private void closeStatusBarIndicator() {
    if (myStatusBarTaskInfo != null) {
      indicator.finish(myStatusBarTaskInfo);
      myStatusBarTaskInfo = null;
    }
  }
}