// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions;

import com.intellij.execution.ExecutionException;
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
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.Restarter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.Map;

import static com.intellij.openapi.util.Pair.pair;
import static com.intellij.util.containers.ContainerUtil.newHashMap;

/**
 * @author yole
 */
public class CreateLauncherScriptAction extends DumbAwareAction {
  private static final Logger LOG = Logger.getInstance(CreateLauncherScriptAction.class);

  private static class Holder {
    private static final String INTERPRETER_NAME =
      PathEnvironmentVariableUtil.findInPath("python") != null ? "python" :
      PathEnvironmentVariableUtil.findInPath("python3") != null ? "python3" : null;
  }

  public static boolean isAvailable() {
    return SystemInfo.isUnix && !ExternalUpdateManager.isRoaming() && Holder.INTERPRETER_NAME != null;
  }

  @Override
  public void update(@NotNull AnActionEvent event) {
    boolean enabled = SystemInfo.isUnix &&
                      (!ExternalUpdateManager.isRoaming() || ExternalUpdateManager.ACTUAL == ExternalUpdateManager.TOOLBOX) &&
                      Holder.INTERPRETER_NAME != null;
    event.getPresentation().setEnabledAndVisible(enabled);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent event) {
    if (!isAvailable()) {
      if (ExternalUpdateManager.ACTUAL == ExternalUpdateManager.TOOLBOX) {
        String title = ApplicationBundle.message("launcher.script.title");
        String message = ApplicationBundle.message("launcher.script.luke");
        Messages.showInfoMessage(event.getProject(), message, title);
      }
      return;
    }

    Project project = event.getProject();

    String title = ApplicationBundle.message("launcher.script.title");
    String prompt = ApplicationBundle.message("launcher.script.prompt", ApplicationNamesInfo.getInstance().getFullProductName());
    String path = Messages.showInputDialog(project, prompt, title, null, defaultScriptPath(), null);
    if (path == null) {
      return;
    }

    if (!path.startsWith("/")) {
      String home = System.getenv("HOME");
      if (home != null && new File(home).isDirectory()) {
        if (path.startsWith("~")) {
          path = home + path.substring(1);
        }
        else {
          path = home + "/" + path;
        }
      }
    }

    File target = new File(path);
    if (target.exists()) {
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
          createLauncherScript(target.getAbsolutePath());
        }
        catch (Exception e) {
          reportFailure(e, project);
        }
      }
    }.queue();
  }

  public static void createLauncherScript(@NotNull String pathName) throws Exception {
    if (!isAvailable()) return;

    File scriptFile = createLauncherScriptFile();
    try {
      File scriptTarget = new File(pathName);

      File scriptTargetDir = scriptTarget.getParentFile();
      assert scriptTargetDir != null : "path: " + pathName;

      if (!(scriptTargetDir.exists() || scriptTargetDir.mkdirs()) || !scriptFile.renameTo(scriptTarget)) {
        String scriptTargetDirPath = scriptTargetDir.getCanonicalPath();
        // copy file and change ownership to root (UID 0 = root, GID 0 = root (wheel on Macs))
        String installationScriptSrc =
          "#!/bin/sh\n" +
          "mkdir -p \"" + scriptTargetDirPath + "\"\n" +
          "install -g 0 -o 0 \"" + scriptFile.getCanonicalPath() + "\" \"" + pathName + "\"";
        File installationScript = ExecUtil.createTempExecutableScript("launcher_installer", ".sh", installationScriptSrc);
        String prompt = ApplicationBundle.message("launcher.script.sudo.prompt", scriptTargetDirPath);
        ProcessOutput result = ExecUtil.sudoAndGetOutput(new GeneralCommandLine(installationScript.getPath()), prompt);
        int exitCode = result.getExitCode();
        if (exitCode != 0) {
          String message = "Launcher script creation failed with " + exitCode;
          String output = result.getStdout();
          if (!StringUtil.isEmptyOrSpaces(output)) message += "\nOutput: " + output.trim();
          throw new RuntimeException(message);
        }
      }
    }
    finally {
      if (scriptFile.exists()) {
        FileUtil.delete(scriptFile);
      }
    }
  }

  public static void reportFailure(@NotNull Exception e, @Nullable Project project) {
    LOG.warn(e);
    String message = ExceptionUtil.getNonEmptyMessage(e, IdeBundle.message("notification.content.internal error"));
    Notifications.Bus.notify(
      new Notification(Notifications.SYSTEM_MESSAGES_GROUP_ID, IdeBundle.message("notification.title.launcher.script.creation.failed"), message, NotificationType.ERROR),
      project);
  }

  private static File createLauncherScriptFile() throws IOException, ExecutionException {
    File starter = Restarter.getIdeStarter();
    if (starter == null) throw new IOException(ApplicationBundle.message("desktop.entry.script.missing", PathManager.getBinPath()));

    ClassLoader loader = CreateLauncherScriptAction.class.getClassLoader();
    assert loader != null;
    Map<String, String> variables = newHashMap(
      pair("$PYTHON$", Holder.INTERPRETER_NAME),
      pair("$CONFIG_PATH$", PathManager.getConfigPath()),
      pair("$SYSTEM_PATH$", PathManager.getSystemPath()),
      pair("$RUN_PATH$", starter.getPath()));
    String launcherContents = StringUtil.convertLineSeparators(ExecUtil.loadTemplate(loader, "launcher.py", variables));

    return ExecUtil.createTempExecutableScript("launcher", "", launcherContents);
  }

  public static String defaultScriptPath() {
    String scriptName = ApplicationNamesInfo.getInstance().getDefaultLauncherName();
    if (StringUtil.isEmptyOrSpaces(scriptName)) scriptName = StringUtil.toLowerCase(ApplicationNamesInfo.getInstance().getProductName());
    return "/usr/local/bin/" + scriptName;
  }
}