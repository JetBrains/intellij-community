/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import com.intellij.execution.util.ExecUtil;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
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
    return SystemInfo.isUnix;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    boolean canCreateScript = isAvailable();
    Presentation presentation = e.getPresentation();
    presentation.setVisible(canCreateScript);
    presentation.setEnabled(canCreateScript);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    if (!isAvailable()) return;

    Project project = e.getProject();
    CreateLauncherScriptDialog dialog = new CreateLauncherScriptDialog(project);
    dialog.show();
    if (!dialog.isOK()) {
      return;
    }

    String path = dialog.myPathField.getText();
    assert path != null;
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

    String name = dialog.myNameField.getText();
    assert name != null;
    File target = new File(path, name);
    if (target.exists()) {
      String message = ApplicationBundle.message("launcher.script.overwrite", target);
      String title = ApplicationBundle.message("launcher.script.title");
      if (Messages.showOkCancelDialog(project, message, title, Messages.getQuestionIcon()) != Messages.OK) {
        return;
      }
    }

    createLauncherScript(project, target.getAbsolutePath());
  }

  public static void createLauncherScript(Project project, String pathName) {
    if (!isAvailable()) return;

    try {
      File scriptFile = createLauncherScriptFile();
      File scriptTarget = new File(pathName);

      File scriptTargetDir = scriptTarget.getParentFile();
      assert scriptTargetDir != null;
      if (!(scriptTargetDir.exists() || scriptTargetDir.mkdirs()) || !scriptFile.renameTo(scriptTarget)) {
        String scriptTargetDirPath = scriptTargetDir.getCanonicalPath();
        // copy file and change ownership to root (UID 0 = root, GID 0 = root (wheel on Macs))
        String installationScriptSrc =
          "#!/bin/sh\n" +
          "mkdir -p \"" + scriptTargetDirPath + "\"\n" +
          "install -g 0 -o 0 \"" + scriptFile.getCanonicalPath() + "\" \"" + pathName + "\"";
        File installationScript = ExecUtil.createTempExecutableScript("launcher_installer", ".sh", installationScriptSrc);
        String prompt = ApplicationBundle.message("launcher.script.sudo.prompt", scriptTargetDirPath);
        ExecUtil.sudoAndGetOutput(new GeneralCommandLine(installationScript.getPath()), prompt);
      }
    }
    catch (Exception e) {
      String message = e.getMessage();
      if (!StringUtil.isEmptyOrSpaces(message)) {
        LOG.warn(e);
        Notifications.Bus.notify(
          new Notification(Notifications.SYSTEM_MESSAGES_GROUP_ID, "Failed to create launcher script", message, NotificationType.ERROR),
          project);
      }
      else {
        LOG.error(e);
      }
    }
  }

  private static File createLauncherScriptFile() throws IOException, ExecutionException {
    String runPath = PathManager.getHomePath();
    String productName = ApplicationNamesInfo.getInstance().getProductName().toLowerCase(Locale.US);
    if (!SystemInfo.isMac) runPath += "/bin/" + productName + ".sh";
    else if (runPath.endsWith(CONTENTS)) runPath += "/MacOS/" + productName;

    ClassLoader loader = CreateLauncherScriptAction.class.getClassLoader();
    assert loader != null;
    Map<String, String> variables = newHashMap(pair("$CONFIG_PATH$", PathManager.getConfigPath()), pair("$RUN_PATH$", runPath));
    String launcherContents = StringUtil.convertLineSeparators(ExecUtil.loadTemplate(loader, "launcher.py", variables));

    return ExecUtil.createTempExecutableScript("launcher", "", launcherContents);
  }

  public static String defaultScriptName() {
    String scriptName = ApplicationNamesInfo.getInstance().getScriptName();
    return StringUtil.isEmptyOrSpaces(scriptName) ? "idea" : scriptName;
  }

  public static class CreateLauncherScriptDialog extends DialogWrapper {
    private JPanel myMainPanel;
    private JTextField myNameField;
    private JTextField myPathField;
    private JLabel myTitle;

    protected CreateLauncherScriptDialog(Project project) {
      super(project);
      init();
      setTitle(ApplicationBundle.message("launcher.script.title"));
      String productName = ApplicationNamesInfo.getInstance().getProductName();
      myTitle.setText(myTitle.getText().replace("$APP_NAME$", productName));
      myNameField.setText(defaultScriptName());
    }

    @Override
    protected JComponent createCenterPanel() {
      return myMainPanel;
    }
  }
}
