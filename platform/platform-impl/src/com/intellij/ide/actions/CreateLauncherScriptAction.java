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

import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.ex.ApplicationEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.CharsetToolkit;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.swing.*;
import java.io.File;
import java.io.IOException;

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
    createLauncherScript(project, dialog.myNameField.getText(), dialog.myPathField.getText());
  }

  public static void createLauncherScript(Project project, String name, String path) {
    try {
      final File scriptFile = createLauncherScriptFile();

      final String mvCommand = "mv -f " + scriptFile + " " + path + "/" + name;
      if (SystemInfo.isMac) {
        final ScriptEngine engine = new ScriptEngineManager(null).getEngineByName("AppleScript");
        if (engine == null) {
          throw new IOException("Could not find AppleScript engine");
        }
        engine.eval("do shell script \"" + mvCommand + "\" with administrator privileges");
      }
      else if (SystemInfo.isUnix) {
        GeneralCommandLine cmdLine = new GeneralCommandLine();
        if (SystemInfo.isGnome) {
          cmdLine.setExePath("gksudo");
          cmdLine.addParameters("--message",
                                "In order to create a launcher script in " +
                                path +
                                ", please enter your administrator password:");
        }
        else if (SystemInfo.isKDE) {
          cmdLine.setExePath("kdesudo");
        }
        else {
          Messages.showMessageDialog("Unsupported graphical environment. Please execute the following command from the shell:\n" + mvCommand,
                                     "Create Launcher Script",
                                     Messages.getInformationIcon());
          return;
        }
        cmdLine.addParameter(mvCommand);
        cmdLine.createProcess();
      }
    }
    catch (Exception e) {
      LOG.info(e);
      Messages.showErrorDialog(project, "Failed to create launcher script: " + e.getMessage(), "Create Launcher Script");
    }
  }

  private static File createLauncherScriptFile() throws IOException {
    final File tempFile = FileUtil.createTempFile("launcher", "");
    StringBuilder textBuilder = new StringBuilder("#!/bin/bash\n");
    String homePath = PathManager.getHomePath().replace(" ", "\\ ");
    String productName = ApplicationNamesInfo.getInstance().getProductName().toLowerCase();
    if (SystemInfo.isMac) {
      textBuilder.append("cd ").append(homePath).append("/Contents/MacOS/\n");
      textBuilder.append("./").append(productName);
    }
    else {
      textBuilder.append("cd ").append(homePath).append("/bin\n");
      textBuilder.append("./").append(productName).append(".sh");
    }
    textBuilder.append(" $@");
    FileUtil.writeToFile(tempFile, textBuilder.toString().getBytes(CharsetToolkit.UTF8_CHARSET));
    return tempFile;
  }

  @Override
  public void update(AnActionEvent e) {
    boolean canCreateScript = /*SystemInfo.isUnix || SystemInfo.isMac */ ((ApplicationEx) ApplicationManager.getApplication()).isInternal();
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
      if (productName.equalsIgnoreCase("RubyMine")) {
        myNameField.setText("mine");
      }
      else if (productName.equalsIgnoreCase("PyCharm")) {
        myNameField.setText("charm");
      }
    }

    @Override
    protected JComponent createCenterPanel() {
      return myMainPanel;
    }
  }
}
