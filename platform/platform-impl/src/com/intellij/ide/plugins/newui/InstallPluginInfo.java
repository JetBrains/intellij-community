// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
final class InstallPluginInfo {

  public final @NotNull BgProgressIndicator indicator;
  private final @NotNull IdeaPluginDescriptor myDescriptor;
  private @Nullable MyPluginModel myPluginModel;
  public final boolean install;
  private TaskInfo myStatusBarTaskInfo;
  private boolean myClosed;
  private static boolean ourShowRestart;

  /**
   * Descriptor that has been loaded synchronously.
   */
  private IdeaPluginDescriptorImpl myInstalledDescriptor;

  InstallPluginInfo(@NotNull BgProgressIndicator indicator,
                    @NotNull IdeaPluginDescriptor descriptor,
                    @NotNull MyPluginModel pluginModel,
                    boolean install) {
    this.indicator = indicator;
    myDescriptor = descriptor;
    myPluginModel = pluginModel;
    this.install = install;
  }

  public synchronized void toBackground(@Nullable StatusBarEx statusBar) {
    myPluginModel = null;
    indicator.removeStateDelegates();
    if (statusBar != null) {
      String title = install ?
                     IdeBundle.message("dialog.title.installing.plugin", myDescriptor.getName()) :
                     IdeBundle.message("dialog.title.updating.plugin", myDescriptor.getName());
      statusBar.addProgress(indicator, myStatusBarTaskInfo = OneLineProgressIndicator.task(title));
    }
  }

  public synchronized void fromBackground(@NotNull MyPluginModel pluginModel) {
    myPluginModel = pluginModel;
    ourShowRestart = false;
    closeStatusBarIndicator();
  }

  public static void showRestart() {
    ourShowRestart = true;
  }

  public synchronized void finish(boolean success, boolean cancel, boolean showErrors, boolean restartRequired) {
    if (myClosed) {
      return;
    }
    if (myPluginModel == null) {
      MyPluginModel.finishInstall(myDescriptor);
      closeStatusBarIndicator();
      if (success && !cancel && restartRequired) {
        ourShowRestart = true;
      }
      if (MyPluginModel.myInstallingInfos.isEmpty() && ourShowRestart) {
        ourShowRestart = false;
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