// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions;

import com.intellij.ide.GeneralSettings;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.ApplicationEx;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.ui.Messages;
import org.jetbrains.annotations.NotNull;

public class RestartIdeAction extends AnAction implements DumbAware {
  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {

    boolean result;
    if (GeneralSettings.getInstance().isConfirmExit()) {
      result = Messages.showYesNoDialog(
        IdeBundle.message("dialog.message.restart.ide"),
        IdeBundle.message("dialog.title.restart.ide"),
        IdeBundle.message("dialog.action.restart.yes"),
        IdeBundle.message("dialog.action.restart.cancel"),
        Messages.getWarningIcon()
      ) == Messages.YES;
    } else {
      result = true;
    }

    if (result) {
      final ApplicationEx app = (ApplicationEx)ApplicationManager.getApplication();

      app.restart(true);
    }
  }
}
