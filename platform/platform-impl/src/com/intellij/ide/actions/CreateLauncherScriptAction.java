// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions;

import com.intellij.execution.configurations.PathEnvironmentVariableUtil;
import com.intellij.execution.process.ProcessIOExecutorService;
import com.intellij.help.impl.HelpManagerImpl;
import com.intellij.ide.AppLifecycleListener;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationAction;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.updateSettings.impl.ExternalUpdateManager;
import com.intellij.openapi.util.SystemInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Files;
import java.nio.file.Path;

final class CreateLauncherScriptAction extends DumbAwareAction implements ActionRemoteBehaviorSpecification.Frontend {
  private static final String TOPIC = "Working_with_the_IDE_Features_from_Command_Line";

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent event) {
    showInstructions(event.getProject());
  }

  private static void showInstructions(@Nullable Project project) {
    String message;
    if (ExternalUpdateManager.ACTUAL == ExternalUpdateManager.TOOLBOX) {
      message = ApplicationBundle.message("cli.launcher.message.toolbox");
    }
    else if (ExternalUpdateManager.ACTUAL == ExternalUpdateManager.SNAP) {
      var name = ApplicationNamesInfo.getInstance().getScriptName();
      message = ApplicationBundle.message("cli.launcher.message.snap", name);
    }
    else if (SystemInfo.isWindows) {
      var dir = Path.of(PathManager.getBinPath());
      var name1 = ApplicationNamesInfo.getInstance().getScriptName() + ".bat";
      var name2 = ApplicationNamesInfo.getInstance().getScriptName() + "64.exe";
      var url = ((HelpManagerImpl)HelpManager.getInstance()).getHelpUrl(TOPIC);
      message = ApplicationBundle.message("cli.launcher.message.windows", dir, name1, name2, url);
    }
    else if (SystemInfo.isMac) {
      var dir = Path.of(PathManager.getHomePath()).resolve("MacOS");
      var name = ApplicationNamesInfo.getInstance().getScriptName();
      var url = ((HelpManagerImpl)HelpManager.getInstance()).getHelpUrl(TOPIC);
      message = ApplicationBundle.message("cli.launcher.message.unix", dir, name, url);
    }
    else {
      var dir = Path.of(PathManager.getBinPath());
      var name = ApplicationNamesInfo.getInstance().getScriptName() + ".sh";
      var url = ((HelpManagerImpl)HelpManager.getInstance()).getHelpUrl(TOPIC);
      message = ApplicationBundle.message("cli.launcher.message.unix", dir, name, url);
    }
    Messages.showInfoMessage(project, message, ApplicationBundle.message("cli.launcher.message.title"));
  }

  static final class ObsoleteScriptLookupTask implements AppLifecycleListener {
    private static final String MARKER1 = "{0} [-l|--line line] [project_dir|--temp-project] [-w|--wait] file[:line]";
    private static final String MARKER2 = "def try_activate_instance(args):";

    @Override
    public void appStarted() {
      var app = ApplicationManager.getApplication();
      if (!(app.isCommandLine() || app.isHeadlessEnvironment() || app.isUnitTestMode())) {
        ProcessIOExecutorService.INSTANCE.execute(() -> {
          try {
            var launcherName = ApplicationNamesInfo.getInstance().getScriptName();
            var scriptName = switch (launcherName) {
              case "phpstorm" -> "pstorm";
              case "pycharm" -> "charm";
              case "rubymine" -> "mine";
              default -> launcherName;
            };
            var scriptFile = PathEnvironmentVariableUtil.findInPath(scriptName);
            if (scriptFile != null) {
              var content = Files.readString(scriptFile.toPath());
              if (content.contains(MARKER1) && content.contains(MARKER2) && !app.isDisposed()) {
                var title = ApplicationBundle.message("cli.launcher.obsolete.title");
                var message = ApplicationBundle.message("cli.launcher.obsolete.message", scriptFile);
                new Notification("IDE and Plugin Updates", title, message, NotificationType.INFORMATION)
                  .addAction(NotificationAction.createSimple(ApplicationBundle.message("cli.launcher.obsolete.action"), () -> showInstructions(null)))
                  .notify(null);
              }
            }
          }
          catch (Exception e) {
            Logger.getInstance(ObsoleteScriptLookupTask.class).info(e);
          }
        });
      }
    }
  }
}
