// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions;

import com.intellij.CommonBundle;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.logsUploader.LogsPacker;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.idea.ActionsBundle;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DoNotAskOption;
import com.intellij.openapi.ui.MessageDialogBuilder;
import com.intellij.openapi.ui.Messages;
import com.intellij.util.ui.IoErrorText;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Path;

public final class CollectZippedLogsAction extends AnAction implements DumbAware {
  private static final String CONFIRMATION_DIALOG = "zipped.logs.action.show.confirmation.dialog";
  public static final String NOTIFICATION_GROUP = "Collect Zipped Logs";

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getProject();

    boolean doNotShowDialog = PropertiesComponent.getInstance().getBoolean(CONFIRMATION_DIALOG);
    if (!doNotShowDialog) {
      String title = IdeBundle.message("collect.logs.sensitive.title");
      String message = IdeBundle.message("collect.logs.sensitive.text");
      boolean confirmed = MessageDialogBuilder.okCancel(title, message)
        .yesText(ActionsBundle.message("action.RevealIn.name.other", RevealFileAction.getFileManagerName()))
        .noText(CommonBundle.getCancelButtonText())
        .icon(Messages.getWarningIcon())
        .doNotAsk(new DoNotAskOption.Adapter() {
          @Override
          public void rememberChoice(boolean selected, int exitCode) {
            PropertiesComponent.getInstance().setValue(CONFIRMATION_DIALOG, selected);
          }
        })
        .ask(project);
      if (!confirmed) {
        return;
      }
    }

    ProgressManager.getInstance().runProcessWithProgressSynchronously(() -> {
      try {
        Path logs = LogsPacker.packLogs(project);
        if (RevealFileAction.isSupported()) {
          RevealFileAction.openFile(logs);
        }
        else {
          new Notification(NOTIFICATION_GROUP, IdeBundle.message("collect.logs.notification.success", logs), NotificationType.INFORMATION).notify(project);
        }
      }
      catch (IOException x) {
        Logger.getInstance(getClass()).warn(x);
        String message = IdeBundle.message("collect.logs.notification.error", IoErrorText.message(x));
        new Notification(NOTIFICATION_GROUP, message, NotificationType.ERROR).notify(project);
      }
    }, IdeBundle.message("collect.logs.progress.title"), true, project);
  }


  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }
}
