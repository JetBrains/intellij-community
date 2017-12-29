/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.settingsSummary.ProblemType;
import com.intellij.util.io.ZipUtil;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.zip.ZipOutputStream;

public class CollectZippedLogsAction extends AnAction implements DumbAware {
  private static final String CONFIRMATION_DIALOG = "zipped.logs.action.show.confirmation.dialog";

  @Override
  public void actionPerformed(@NotNull final AnActionEvent e) {
    final Project project = e.getProject();

    final boolean doNotShowDialog = PropertiesComponent.getInstance().getBoolean(CONFIRMATION_DIALOG);

    try {
      final File zippedLogsFile = createZip(project);
      if (!doNotShowDialog) {
        Messages.showIdeaMessageDialog(
          project, "Included logs and settings may contain sensitive data.", "Sensitive Data",
          new String[]{"Show in " + ShowFilePathAction.getFileManagerName()}, 1, Messages.getWarningIcon(),
          new DialogWrapper.DoNotAskOption.Adapter() {
            @Override
            public void rememberChoice(final boolean selected, final int exitCode) {
              PropertiesComponent.getInstance().setValue(CONFIRMATION_DIALOG, selected);
            }
          }
        );
      }
      if (ShowFilePathAction.isSupported()) {
        ShowFilePathAction.openFile(zippedLogsFile);
      }
      else {
        Messages.showInfoMessage(zippedLogsFile.getAbsolutePath(), "Log File");
      }
    }
    catch (final IOException exception) {
      Messages.showErrorDialog("Can't create zip file with logs: " + exception.getLocalizedMessage(), "Can't Create File");
    }
  }

  @NotNull
  private static File createZip(final Project project) throws IOException {
    File settingsTempFile = null;
    final File zippedLogsFile = FileUtil.createTempFile("logs-" + getDate(), ".zip");
    try (ZipOutputStream zipOutputStream = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(zippedLogsFile)))) {
      ZipUtil.addFileOrDirRecursively(zipOutputStream, null, new File(PathManager.getLogPath()), "", null, null);
      settingsTempFile = dumpSettingsToFile(project);
      ZipUtil.addFileToZip(zipOutputStream, settingsTempFile, "settings.txt", null, null);
    }
    catch (final IOException exception) {
      //noinspection ResultOfMethodCallIgnored
      zippedLogsFile.delete();
      throw exception;
    }
    finally {
      if (settingsTempFile != null) {
        //noinspection ResultOfMethodCallIgnored
        settingsTempFile.delete();
      }
    }
    return zippedLogsFile;
  }

  @NotNull
  private static File dumpSettingsToFile(final Project project) throws IOException {
    final File settingsTempFile = FileUtil.createTempFile("settings" + getDate(), ".txt");
    for (ProblemType problemType : ProblemType.EP_SETTINGS.getExtensions()) {
      String settingString = problemType.collectInfo(project);
      FileUtil.appendToFile(settingsTempFile, settingString + '\n');
    }
    return settingsTempFile;
  }

  @NotNull
  private static String getDate() {
    return new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date());
  }

  @Override
  public void update(AnActionEvent e) {
    Presentation presentation = e.getPresentation();
    presentation.setText(getActionName());
  }

  @NotNull
  private static String getActionName() {
    return "Compress Logs and Show in " + ShowFilePathAction.getFileManagerName();
  }
}
