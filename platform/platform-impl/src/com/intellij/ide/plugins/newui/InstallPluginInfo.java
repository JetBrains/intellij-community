// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins.newui;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.IdeaPluginDescriptorImpl;
import com.intellij.ide.plugins.PluginManagerConfigurable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.TaskInfo;
import com.intellij.openapi.wm.ex.StatusBarEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Alexander Lobas
 */
@SuppressWarnings({"AssignmentToStaticFieldFromInstanceMethod", "FieldAccessedSynchronizedAndUnsynchronized"})
public class InstallPluginInfo {
  public final BgProgressIndicator indicator = new BgProgressIndicator();
  private final IdeaPluginDescriptor myDescriptor;
  private MyPluginModel myPluginModel;
  public final boolean install;
  public final IdeaPluginDescriptor updateDescriptor;
  private TaskInfo myStatusBarTaskInfo;
  private boolean myClosed;
  private static boolean myShowRestart;

  /**
   * Descriptor that has been loaded synchronously.
   */
  private IdeaPluginDescriptorImpl myInstalledDescriptor;

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
    indicator.removeStateDelegates();
    if (statusBar != null) {
      String title = install
                     ? IdeBundle.message("dialog.title.installing.plugin", myDescriptor.getName())
                     : IdeBundle.message("dialog.title.updating.plugin", myDescriptor.getName());
      statusBar.addProgress(indicator, myStatusBarTaskInfo = OneLineProgressIndicator.task(title));
    }
  }

  public synchronized void fromBackground(@NotNull MyPluginModel pluginModel) {
    myPluginModel = pluginModel;
    myShowRestart = false;
    closeStatusBarIndicator();
  }

  public static void showRestart() {
    myShowRestart = true;
  }

  public synchronized void finish(boolean success, boolean cancel, boolean showErrors, boolean restartRequired) {
    if (myClosed) {
      return;
    }
    if (myPluginModel == null) {
      MyPluginModel.finishInstall(myDescriptor);
      closeStatusBarIndicator();
      if (success && !cancel && restartRequired) {
        myShowRestart = true;
      }
      if (MyPluginModel.myInstallingInfos.isEmpty() && myShowRestart) {
        myShowRestart = false;
        ApplicationManager.getApplication().invokeLater(() -> PluginManagerConfigurable.shutdownOrRestartApp());
      }
    }
    else if (!cancel) {
      myPluginModel.finishInstall(myDescriptor, myInstalledDescriptor, success, showErrors, restartRequired);
    }
  }

  private void closeStatusBarIndicator() {
    if (myStatusBarTaskInfo != null) {
      indicator.finish(myStatusBarTaskInfo);
      myStatusBarTaskInfo = null;
    }
  }

  public void close() {
    myClosed = true;
  }

  public IdeaPluginDescriptor getDescriptor() {
    return myDescriptor;
  }

  public void setInstalledDescriptor(IdeaPluginDescriptorImpl installedDescriptor) {
    this.myInstalledDescriptor = installedDescriptor;
  }
}