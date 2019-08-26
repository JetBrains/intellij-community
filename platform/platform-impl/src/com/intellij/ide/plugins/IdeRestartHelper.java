// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.ui.Messages;
import org.jetbrains.annotations.NotNull;

public class IdeRestartHelper {
  @Messages.YesNoResult
  public static int showRestartDialog() {
    return showRestartDialog(IdeBundle.message("update.notifications.title"));
  }

  @Messages.YesNoResult
  public static int showRestartDialog(@NotNull String title) {
    String action = IdeBundle.message(ApplicationManager.getApplication().isRestartCapable() ? "ide.restart.action" : "ide.shutdown.action");
    String message = IdeBundle.message("ide.restart.required.message", action, ApplicationNamesInfo.getInstance().getFullProductName());
    return Messages.showYesNoDialog(message, title, action, IdeBundle.message("ide.notnow.action"), Messages.getQuestionIcon());
  }

  public static void shutdownOrRestartApp() {
    shutdownOrRestartApp(IdeBundle.message("update.notifications.title"));
  }

  public static void shutdownOrRestartApp(@NotNull String title) {
    if (showRestartDialog(title) == Messages.YES) {
      ApplicationManagerEx.getApplicationEx().restart(true);
    }
  }
}
