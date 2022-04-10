// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions;

import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.configurations.PathEnvironmentVariableUtil;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.execution.util.ExecUtil;
import com.intellij.ide.IdeBundle;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.updateSettings.impl.ExternalUpdateManager;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.NioFiles;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.Restarter;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Map;

public class CreateLauncherScriptAction extends DumbAwareAction {
  @Override
  public void update(@NotNull AnActionEvent event) {
    boolean enabled = SystemInfo.isUnix &&
                      (!ExternalUpdateManager.isRoaming() || ExternalUpdateManager.ACTUAL == ExternalUpdateManager.TOOLBOX);
    event.getPresentation().setEnabledAndVisible(enabled);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent event) {
    if (!SystemInfo.isUnix || ExternalUpdateManager.isRoaming()) {
      if (ExternalUpdateManager.ACTUAL == ExternalUpdateManager.TOOLBOX) {
        String title = ApplicationBundle.message("launcher.script.title");
        String message = ApplicationBundle.message("launcher.script.luke");
        Messages.showInfoMessage(event.getProject(), message, title);
      }
      return;
    }

    Project project = event.getProject();
    ApplicationNamesInfo appNames = ApplicationNamesInfo.getInstance();
    String title = ApplicationBundle.message("launcher.script.title");
    String prompt = ApplicationBundle.message("launcher.script.prompt", appNames.getFullProductName());
    String scriptName = appNames.getDefaultLauncherName();
    if (scriptName == null || scriptName.isBlank()) scriptName = appNames.getProductName().toLowerCase(Locale.ENGLISH);
    String path = "/usr/local/bin/" + scriptName;
    path = Messages.showInputDialog(project, prompt, title, null, path, null);
    if (path == null) return;

    if (!path.startsWith("/")) {
      String home = System.getenv("HOME");
      if (home != null && Files.isDirectory(Path.of(home))) {
        path = path.startsWith("~/") ? home + path.substring(1) : home + '/' + path;
      }
    }

    Path target = Path.of(path);
    if (Files.exists(target)) {
      String message = ApplicationBundle.message("launcher.script.overwrite", target);
      String ok = ApplicationBundle.message("launcher.script.overwrite.button");
      if (Messages.showOkCancelDialog(project, message, title, ok, Messages.getCancelButton(), Messages.getQuestionIcon()) != Messages.OK) {
        return;
      }
    }

    new Task.Backgroundable(project, ApplicationBundle.message("launcher.script.progress")) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        try {
          createLauncherScript(target);
        }
        catch (Exception e) {
          Logger.getInstance(CreateLauncherScriptAction.class).warn(e);
          String title = IdeBundle.message("notification.title.launcher.script.creation.failed");
          String message = ExceptionUtil.getNonEmptyMessage(e, IdeBundle.message("notification.content.internal error"));
          new Notification(Notifications.SYSTEM_MESSAGES_GROUP_ID, title, message, NotificationType.ERROR).notify(project);
        }
      }
    }.queue();
  }

  private static void createLauncherScript(Path scriptTarget) throws Exception {
    Path scriptTargetDir = scriptTarget.getParent();
    if (scriptTargetDir == null) throw new IllegalArgumentException("Invalid path: " + scriptTarget);

    Path starter = Restarter.getIdeStarter();
    if (starter == null) throw new IOException(ApplicationBundle.message("desktop.entry.script.missing", PathManager.getBinPath()));

    String interpreter = PathEnvironmentVariableUtil.findInPath("python3") != null ? "python3" :
                         PathEnvironmentVariableUtil.findInPath("python") != null ? "python" :
                         null;
    if (interpreter == null) throw new IOException("Cannot find a Python interpreter");

    ClassLoader loader = CreateLauncherScriptAction.class.getClassLoader();
    assert loader != null;
    Map<String, String> variables = Map.of(
      "$PYTHON$", interpreter,
      "$CONFIG_PATH$", PathManager.getConfigPath(),
      "$SYSTEM_PATH$", PathManager.getSystemPath(),
      "$RUN_PATH$", starter.toString());
    String launcherContents = StringUtil.convertLineSeparators(ExecUtil.loadTemplate(loader, "launcher.py", variables));
    Path scriptFile = ExecUtil.createTempExecutableScript("launcher", "", launcherContents).toPath();

    Path installationScript = null;
    try {
      NioFiles.createDirectories(scriptTargetDir);
      Files.move(scriptFile, scriptTarget, StandardCopyOption.REPLACE_EXISTING);
    }
    catch (IOException e) {
      Logger.getInstance(CreateLauncherScriptAction.class).info(scriptTarget.toString(), e);

      String scriptTargetDirPath = scriptTargetDir.toString();
      String installationScriptSrc =
        "#!/bin/sh\n" +
        "mkdir -p '" + scriptTargetDirPath + "' && install -g 0 -o 0 '" + scriptFile + "' '" + scriptTarget + "'";
      installationScript = ExecUtil.createTempExecutableScript("launcher_installer", ".sh", installationScriptSrc).toPath();
      GeneralCommandLine command = new GeneralCommandLine(installationScript.toString()).withRedirectErrorStream(true);
      String prompt = ApplicationBundle.message("launcher.script.sudo.prompt", scriptTargetDirPath);
      ProcessOutput result = ExecUtil.sudoAndGetOutput(command, prompt);
      if (result.getExitCode() != 0) {
        String message = "Launcher script creation failed with " + result.getExitCode();
        String output = result.getStdout();
        if (!output.isBlank()) message += "\nOutput: " + output.trim();
        throw new RuntimeException(message);
      }
    }
    finally {
      if (installationScript != null) {
        Files.deleteIfExists(installationScript);
      }
      Files.deleteIfExists(scriptFile);
    }
  }
}
