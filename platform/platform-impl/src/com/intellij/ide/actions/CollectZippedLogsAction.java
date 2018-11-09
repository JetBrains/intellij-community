// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.settingsSummary.ProblemType;
import com.intellij.util.SystemProperties;
import com.intellij.util.io.Compressor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;

public class CollectZippedLogsAction extends AnAction implements DumbAware {
  private static final String CONFIRMATION_DIALOG = "zipped.logs.action.show.confirmation.dialog";

  @Override
  public void actionPerformed(@NotNull final AnActionEvent e) {
    final Project project = e.getProject();

    final boolean doNotShowDialog = PropertiesComponent.getInstance().getBoolean(CONFIRMATION_DIALOG);

    try {
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
      final File zippedLogsFile =
        ProgressManager.getInstance().run(new Task.WithResult<File, IOException>(project, "Collecting Log Files", false) {
          @Override
          protected File compute(@NotNull ProgressIndicator indicator) throws IOException {
            indicator.setIndeterminate(true);
            return createZip(project);
          }
        });
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
  private static File createZip(@Nullable Project project) throws IOException {
    File zippedLogsFile = FileUtil.createTempFile("logs-" + getDate(), ".zip");

    try (Compressor zip = new Compressor.Zip(zippedLogsFile)) {
      zip.addDirectory(new File(PathManager.getLogPath()));

      if (project != null) {
        StringBuilder settings = new StringBuilder();
        for (ProblemType problemType : ProblemType.EP_SETTINGS.getExtensions()) {
          settings.append(problemType.collectInfo(project)).append('\n');
        }
        zip.addFile("settings.txt", settings.toString().getBytes(StandardCharsets.UTF_8));
      }

      for (File javaErrorLog : getJavaErrorLogs()) {
        zip.addFile(javaErrorLog.getName(), javaErrorLog);
      }
    }
    catch (IOException exception) {
      FileUtil.delete(zippedLogsFile);
      throw exception;
    }

    return zippedLogsFile;
  }

  private static File[] getJavaErrorLogs() {
    return new File(SystemProperties.getUserHome())
      .listFiles(file -> file.isFile() && file.getName().startsWith("java_error_in") && !file.getName().endsWith("hprof"));
  }

  @NotNull
  private static String getDate() {
    return new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date());
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    Presentation presentation = e.getPresentation();
    presentation.setText(getActionName());
  }

  @NotNull
  private static String getActionName() {
    return "Compress Logs and Show in " + ShowFilePathAction.getFileManagerName();
  }
}