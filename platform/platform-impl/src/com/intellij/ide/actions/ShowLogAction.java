// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions;

import com.intellij.idea.ActionsBundle;
import com.intellij.idea.LoggerFactory;
import com.intellij.notification.NotificationAction;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.NlsActions;
import org.jetbrains.annotations.NotNull;

/**
 * @author pegov
 */
public class ShowLogAction extends AnAction implements DumbAware {
  public static @NotNull @NlsActions.ActionText String getActionName() {
    return ActionsBundle.message("show.log.in.action.text", RevealFileAction.getFileManagerName());
  }

  public static boolean isSupported() {
    return RevealFileAction.isSupported();
  }

  public static void showLog() {
    RevealFileAction.openFile(LoggerFactory.getLogFilePath());
  }

  public static @NotNull NotificationAction notificationAction() {
    return NotificationAction.createSimpleExpiring(ActionsBundle.message("show.log.notification.text"), ShowLogAction::showLog);
  }

  public ShowLogAction() {
    getTemplatePresentation().setText(getActionName());
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    Presentation presentation = e.getPresentation();
    presentation.setVisible(isSupported());
    presentation.setText(getActionName());
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    showLog();
  }
}
