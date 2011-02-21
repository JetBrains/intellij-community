/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.CharsetToolkit;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

/**
 * @author yole
 */
public class CreateLauncherScriptAction extends AnAction {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.actions.CreateLauncherScriptAction");

  @Override
  public void actionPerformed(AnActionEvent e) {
    Project project = e.getData(PlatformDataKeys.PROJECT);
    showDialog(project);
  }

  public static void showDialog(Project project) {
    CreateLauncherScriptDialog dialog = new CreateLauncherScriptDialog(project);
    dialog.show();
    if (dialog.getExitCode() != DialogWrapper.OK_EXIT_CODE) {
      return;
    }
    createLauncherScript(project, new File(dialog.myPathField.getText(), dialog.myNameField.getText()).getPath());
  }

  public static void createLauncherScript(Project project, String pathname) {
    try {
      final File scriptFile = createLauncherScriptFile();
      File scriptTarget = new File(pathname);
      if (scriptTarget.exists()) {
        int rc = Messages.showOkCancelDialog(project, "The file " + scriptTarget + " already exists. Would you like to overwrite it?",
                                             "Create Launcher Script", Messages.getQuestionIcon());
        if (rc != 0) {
          return;
        }
      }

      if (!scriptFile.renameTo(scriptTarget)) {
        if (SystemInfo.isUnix) {
          final String launcherScriptContainingDirPath = scriptTarget.getParent();
          final String installationScriptSrc =
            // create all intermediate folders
            "mkdir -p " + launcherScriptContainingDirPath + "\n" +
            // Copy file & change owner to root
            // uid 0 = root
            // gid 0 = root || wheel (MacOS)
            "install -g 0 -o 0 " + scriptFile.getCanonicalPath() + " " + pathname;
          final File installationScript = createTempExecutableScript("launcher_installer", installationScriptSrc);

          sudo(installationScript.getCanonicalPath(),
               installationScriptSrc,
               "In order to create a launcher script in " + launcherScriptContainingDirPath + ", please enter your administrator password:");
        }
      }
    }
    catch (Exception e) {
      LOG.info(e);
      Messages.showErrorDialog(project, "Failed to create launcher script: " + e.getMessage(), "Create Launcher Script");
    }
  }

  private static boolean sudo(String installationScriptPath,
                              String installScriptSrc,
                              final String prompt) throws IOException, ScriptException, ExecutionException {
    if (SystemInfo.isMac) {
      final ScriptEngine engine = new ScriptEngineManager(null).getEngineByName("AppleScript");
      if (engine == null) {
        throw new IOException("Could not find AppleScript engine");
      }
      engine.eval("do shell script \"" + installationScriptPath + "\" with administrator privileges");
    }
    else if (SystemInfo.isUnix) {
      GeneralCommandLine cmdLine = new GeneralCommandLine();
      if (SystemInfo.isGnome) {
        cmdLine.setExePath("gksudo");
        cmdLine.addParameters("--message", prompt);
      }
      else if (SystemInfo.isKDE) {
        cmdLine.setExePath("kdesudo");
      }
      else {
        Messages.showMessageDialog("Unsupported graphical environment. Please execute the following command from the shell:\n" + installScriptSrc,
                                   "Create Launcher Script",
                                   Messages.getInformationIcon());
        return true;
      }
      cmdLine.addParameter(installationScriptPath);
      cmdLine.createProcess();
    }
    return false;
  }

  private static File createLauncherScriptFile() throws IOException {
    final File tempFile = FileUtil.createTempFile("launcher", "");

    final InputStream stream = CreateLauncherScriptAction.class.getClassLoader().getResourceAsStream("launcher.py");
    String launcherContents = FileUtil.loadTextAndClose(new InputStreamReader(stream));
    launcherContents = launcherContents.replace("$CONFIG_PATH$", PathManager.getConfigPath());

    String homePath = PathManager.getHomePath();
    String productName = ApplicationNamesInfo.getInstance().getProductName().toLowerCase();
    if (SystemInfo.isMac) {
      // Just use "*.app"
      launcherContents = launcherContents.replace("$RUN_PATH$", homePath);
    }
    else {
      launcherContents = launcherContents.replace("$RUN_PATH$", homePath + "/bin/" + productName + ".sh");
    }
    FileUtil.writeToFile(tempFile, launcherContents.getBytes(CharsetToolkit.UTF8_CHARSET));
    if (!tempFile.setExecutable(true)) {
      throw new IOException("Failed to mark the launcher script as executable");
    }
    return tempFile;
  }

  private static File createTempExecutableScript(final String fileNamePrefix,
                                                 final String source) throws IOException {
    final File tempFile = FileUtil.createTempFile(fileNamePrefix, "");
    FileUtil.writeToFile(tempFile, source.getBytes(CharsetToolkit.UTF8_CHARSET));
    if (!tempFile.setExecutable(true)) {
      throw new IOException("Failed to mark the launcher installation script as executable: script path " + tempFile.getCanonicalPath());
    }
    return tempFile;
  }

  public static String defaultScriptName() {
    String productName = ApplicationNamesInfo.getInstance().getProductName();
    if (productName.equalsIgnoreCase("RubyMine")) {
      return "mine";
    }
    else if (productName.equalsIgnoreCase("PyCharm")) {
      return "charm";
    }
    return "idea";
  }

  @Override
  public void update(AnActionEvent e) {
    boolean canCreateScript = SystemInfo.isUnix || SystemInfo.isMac;
    e.getPresentation().setVisible(canCreateScript);
  }

  public static class CreateLauncherScriptDialog extends DialogWrapper {
    private JPanel myMainPanel;
    private JTextField myNameField;
    private JTextField myPathField;
    private JLabel myTitle;

    protected CreateLauncherScriptDialog(Project project) {
      super(project);
      init();
      setTitle("Create Launcher Script");
      final String productName = ApplicationNamesInfo.getInstance().getProductName();
      myTitle.setText(myTitle.getText().replace("$APPNAME", productName));
      myNameField.setText(defaultScriptName());
    }

    @Override
    protected JComponent createCenterPanel() {
      return myMainPanel;
    }
  }
}
