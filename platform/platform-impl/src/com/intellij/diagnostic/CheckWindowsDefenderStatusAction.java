// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic;

import com.intellij.ide.BrowserUtil;
import com.intellij.ide.actions.ShowLogAction;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.SystemInfo;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

import static com.intellij.notification.NotificationAction.createSimple;
import static com.intellij.notification.NotificationAction.createSimpleExpiring;

@SuppressWarnings("DialogTitleCapitalization")
final class CheckWindowsDefenderStatusAction extends AnAction {
  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    e.getPresentation().setEnabledAndVisible(
      SystemInfo.isWindows &&
      e.getProject() != null &&
      !WindowsDefenderChecker.getInstance().isStatusCheckIgnored(e.getProject()));
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    var project = e.getProject();
    if (project == null) return;

    new Task.Backgroundable(project, DiagnosticBundle.message("defender.config.checking")) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        var checker = WindowsDefenderChecker.getInstance();
        var protection = checker.isRealTimeProtectionEnabled();
        if (protection == null) {
          notification(DiagnosticBundle.message("defender.config.unknown"), NotificationType.WARNING).notify(project);
        }
        else if (protection != Boolean.TRUE) {
          notification(DiagnosticBundle.message("defender.config.disabled"), NotificationType.INFORMATION).notify(project);
        }
        else {
          var paths = checker.getImportantPaths(project);
          var pathList = paths.stream().map(Path::toString).collect(Collectors.joining("<br>&nbsp;&nbsp;", "<br>&nbsp;&nbsp;", ""));

          Notification notification;
          if (checker.canRunScript()) {
            var auto = DiagnosticBundle.message("defender.config.auto");
            var manual = DiagnosticBundle.message("defender.config.manual");
            notification = notification(DiagnosticBundle.message("defender.config.prompt", pathList, auto, manual), NotificationType.INFORMATION)
              .addAction(createSimpleExpiring(auto, () -> updateDefenderConfig(checker, project, paths)))
              .addAction(createSimple(manual, () -> showInstructions(checker)));
          }
          else {
            notification = notification(DiagnosticBundle.message("defender.config.prompt.no.script", pathList), NotificationType.INFORMATION)
              .addAction(createSimple(DiagnosticBundle.message("defender.config.instructions"), () -> showInstructions(checker)));
          }

          notification
            .setImportant(true)
            .setCollapseDirection(Notification.CollapseActionsDirection.KEEP_LEFTMOST)
            .notify(project);
        }
      }
    }.queue();
  }

  private static Notification notification(@NlsContexts.NotificationContent String content, NotificationType type) {
    return new Notification("WindowsDefender", DiagnosticBundle.message("notification.group.defender.config"), content, type);
  }

  private static void updateDefenderConfig(WindowsDefenderChecker checker, Project project, List<Path> paths) {
    new Task.Backgroundable(project, DiagnosticBundle.message("defender.config.progress")) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        var success = checker.excludeProjectPaths(paths);
        if (success) {
          notification(DiagnosticBundle.message("defender.config.success"), NotificationType.INFORMATION)
            .notify(project);
        }
        else {
          notification(DiagnosticBundle.message("defender.config.failed"), NotificationType.WARNING)
            .addAction(ShowLogAction.notificationAction())
            .notify(project);
        }
      }
    }.queue();
  }

  private static void showInstructions(WindowsDefenderChecker checker) {
    BrowserUtil.browse(checker.getConfigurationInstructionsUrl());
  }
}
