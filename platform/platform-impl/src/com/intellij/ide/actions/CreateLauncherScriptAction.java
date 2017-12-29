/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.ide.actions;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.execution.util.ExecUtil;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ExceptionUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.Locale;
import java.util.Map;

import static com.intellij.openapi.util.Pair.pair;
import static com.intellij.util.containers.ContainerUtil.newHashMap;

/**
 * @author yole
 */
public class CreateLauncherScriptAction extends DumbAwareAction {
  private static final Logger LOG = Logger.getInstance(CreateLauncherScriptAction.class);
  private static final String CONTENTS = "/Contents";

  public static boolean isAvailable() {
    return SystemInfo.isUnix && !PathManager.isSnap();
  }

  @Override
  public void update(@NotNull AnActionEvent event) {
    boolean enabled = isAvailable();
    event.getPresentation().setEnabledAndVisible(enabled);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent event) {
    if (!isAvailable()) return;

    Project project = event.getProject();

    String title = ApplicationBundle.message("launcher.script.title");
    String prompt =
      "<html>You can create a launcher script to enable opening files and projects in " +
      ApplicationNamesInfo.getInstance().getFullProductName() + " from the command line.<br>" +
      "Please specify the name of the script and the path where it should be created:</html>";
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
      if (Messages.showOkCancelDialog(project, message, title, Messages.getQuestionIcon()) != Messages.OK) {
        return;
      }
    }

    try {
      createLauncherScript(target.getAbsolutePath());
    }
    catch (Exception e) {
      reportFailure(e, project);
    }
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

  public static void reportFailure(@NotNull Exception e, @Nullable final Project project) {
    LOG.warn(e);
    final String message = ExceptionUtil.getNonEmptyMessage(e, "Internal error");
    Notifications.Bus.notify(
      new Notification(Notifications.SYSTEM_MESSAGES_GROUP_ID, "Launcher Script Creation Failed", message, NotificationType.ERROR),
      project);
  }

  private static File createLauncherScriptFile() throws IOException, ExecutionException {
    String runPath = SystemInfo.isMac ? StringUtil.trimEnd(PathManager.getHomePath(), CONTENTS) : CreateDesktopEntryAction.getLauncherScript();
    if (runPath == null) throw new IOException(ApplicationBundle.message("desktop.entry.script.missing", PathManager.getBinPath()));

    ClassLoader loader = CreateLauncherScriptAction.class.getClassLoader();
    assert loader != null;
    Map<String, String> variables = newHashMap(
      pair("$CONFIG_PATH$", PathManager.getConfigPath()),
      pair("$SYSTEM_PATH$", PathManager.getSystemPath()),
      pair("$RUN_PATH$", runPath));
    String launcherContents = StringUtil.convertLineSeparators(ExecUtil.loadTemplate(loader, "launcher.py", variables));

    return ExecUtil.createTempExecutableScript("launcher", "", launcherContents);
  }

  public static String defaultScriptPath() {
    String scriptName = ApplicationNamesInfo.getInstance().getDefaultLauncherName();
    if (StringUtil.isEmptyOrSpaces(scriptName)) scriptName = ApplicationNamesInfo.getInstance().getProductName().toLowerCase(Locale.US);
    return "/usr/local/bin/" + scriptName;
  }
}