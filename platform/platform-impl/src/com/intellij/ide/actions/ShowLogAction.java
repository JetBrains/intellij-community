// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions;

import com.intellij.idea.ActionsBundle;
import com.intellij.idea.LoggerFactory;
import com.intellij.notification.NotificationAction;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.NlsActions.ActionText;
import org.jetbrains.annotations.NotNull;

public final class ShowLogAction extends AnAction implements DumbAware, ActionRemoteBehaviorSpecification.Frontend {
  public static @NotNull @ActionText String getActionName() {
    return getActionName(false);
  }

  private static @ActionText String getActionName(boolean skipDetection) {
    return ActionsBundle.message("show.log.in.action.text", RevealFileAction.getFileManagerName(skipDetection));
  }

  public static boolean isSupported() {
    return RevealFileAction.isDirectoryOpenSupported();
  }

  public static void showLog() {
    if (RevealFileAction.isSupported()) {
      RevealFileAction.openFile(LoggerFactory.getLogFilePath());
    }
    else {
      RevealFileAction.openDirectory(PathManager.getLogDir());
    }
  }

  public static @NotNull NotificationAction notificationAction() {
    return NotificationAction.createSimpleExpiring(ActionsBundle.message("show.log.notification.text"), ShowLogAction::showLog);
  }

  public ShowLogAction() {
    getTemplatePresentation().setText(getActionName(true));
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    var presentation = e.getPresentation();
    presentation.setVisible(isSupported());
    presentation.setText(getActionName());
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    showLog();
  }
}
