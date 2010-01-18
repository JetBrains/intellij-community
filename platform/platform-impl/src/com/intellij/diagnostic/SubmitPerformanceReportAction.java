/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.diagnostic;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.SystemProperties;
import com.intellij.util.io.ZipUtil;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPReply;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.zip.ZipOutputStream;

/**
 * @author yole
 */
public class SubmitPerformanceReportAction extends AnAction implements DumbAware {
  private final DateFormat myDateFormat = new SimpleDateFormat("yyyyMMdd-HHmmss");

  private static final String MESSAGE_TITLE = "Submit Performance Report";

  public void actionPerformed(final AnActionEvent e) {
    String reportFileName = "perf_" + ApplicationInfo.getInstance().getBuild().asString() + "_" +
                            SystemProperties.getUserName() + "_" + myDateFormat.format(new Date()) + ".zip";
    final File reportPath = new File(SystemProperties.getUserHome(), reportFileName);
    final File logDir = new File(PathManager.getSystemPath(), "log");
    final Project project = e.getData(PlatformDataKeys.PROJECT);

    final boolean[] archiveCreated = new boolean[1];
    final boolean completed = ProgressManager.getInstance().runProcessWithProgressSynchronously(new Runnable() {
      public void run() {
        try {
          ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(reportPath));
          ZipUtil.addDirToZipRecursively(zip, reportPath, logDir, "", new FileFilter() {
            public boolean accept(final File pathname) {
              ProgressManager.checkCanceled();
              
              if (logDir.equals(pathname.getParentFile())) {
                return pathname.getPath().contains("threadDumps");
              }
              return true;
            }
          }, null);
          zip.close();
          archiveCreated[0] = true;
        }
        catch (final IOException ex) {
          ApplicationManager.getApplication().invokeLater(new Runnable() {
            public void run() {
              Messages.showErrorDialog(project, "Failed to create performance report archive: " + ex.getMessage(), MESSAGE_TITLE);
            }
          });
        }
      }
    }, "Collecting Performance Report data", true, project);

    if (!completed ||
        !archiveCreated[0]) {
      return;
    }

    int rc = Messages.showYesNoDialog(project, "The performance report has been saved to\n" + reportPath +
                                               "\n\nWould you like to submit it to JetBrains?", MESSAGE_TITLE,
                                      Messages.getQuestionIcon());
    if (rc == 0) {
      ProgressManager.getInstance().run(new Task.Backgroundable(project, "Uploading Performance Report") {
        public void run(@NotNull final ProgressIndicator indicator) {
          final String error = uploadFileToFTP(reportPath, "ftp.intellij.net", ".uploads", indicator);
          if (error != null) {
            ApplicationManager.getApplication().invokeLater(new Runnable() {
              public void run() {
                Messages.showErrorDialog(error, MESSAGE_TITLE);
              }
            });
          } 
        }
      });
    }
  }

  @Nullable
  private static String uploadFileToFTP(final File reportPath, @NonNls final String ftpSite, @NonNls final String directory,
                                        final ProgressIndicator indicator) {
    FTPClient ftp = new FTPClient();
    ftp.setConnectTimeout(30 * 1000);
    try {
      indicator.setText("Connecting to server...");
      ftp.connect(ftpSite);
      indicator.setText("Connected to server");

      if (!ftp.login("anonymous", "anonymous@jetbrains.com")) {
        return "Failed to login";
      }
      indicator.setText("Logged in");

      // After connection attempt, you should check the reply code to verify
      // success.
      int reply = ftp.getReplyCode();

      if (!FTPReply.isPositiveCompletion(reply)) {
        ftp.disconnect();
        return "FTP server refused connection: " + reply;
      }
      if (!ftp.changeWorkingDirectory(directory)) {
        return "Failed to change directory";
      }

      // else won't work behind FW
      ftp.enterLocalPassiveMode();

      if (!ftp.setFileType(FTPClient.BINARY_FILE_TYPE)) {
        return "Failed to switch to binary mode";
      }

      indicator.setText("Transferring (" + StringUtil.formatFileSize(reportPath.length()) + ")");
      FileInputStream readStream = new FileInputStream(reportPath);
      try {
        if (!ftp.storeFile(reportPath.getName(), readStream)) {
          return "Failed to upload file";
        }
      } catch (IOException e) {
        return "Error during transfer: " + e.getMessage();
      }
      finally {
        readStream.close();
      }
      ftp.logout();
      return null;
    }
    catch (IOException e) {
      return "Failed to upload: " + e.getMessage();
    }
    finally {
      if (ftp.isConnected()) {
        try {
          ftp.disconnect();
        }
        catch (IOException ioe) {
          // do nothing
        }
      }
    }
  }
}
