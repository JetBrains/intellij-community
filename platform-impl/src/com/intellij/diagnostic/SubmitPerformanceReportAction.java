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
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
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
public class SubmitPerformanceReportAction extends AnAction {
  private DateFormat myDateFormat = new SimpleDateFormat("yyyyMMdd-HHmmss");

  public void actionPerformed(final AnActionEvent e) {
    String reportFileName = "perf_" + ApplicationInfo.getInstance().getBuildNumber() + "_" +
                            SystemProperties.getUserName() + "_" + myDateFormat.format(new Date()) + ".zip";
    final File reportPath = new File(SystemProperties.getUserHome(), reportFileName);
    final File logDir = new File(PathManager.getSystemPath(), "log");
    final Project project = e.getData(PlatformDataKeys.PROJECT);
    try {
      ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(reportPath));
      ZipUtil.addDirToZipRecursively(zip, reportPath, logDir, "", new FileFilter() {
        public boolean accept(final File pathname) {
          if (logDir.equals(pathname.getParentFile())) {
            return pathname.getPath().contains("threadDumps");
          }
          return true;
        }
      }, null);
      zip.close();
    }
    catch (IOException ex) {
      Messages.showErrorDialog(project, "Failed to create performance report archive: " + ex.getMessage(),
                               "Submit Performance Report");
      return;
    }
    int rc = Messages.showYesNoDialog(project, "The performance report has been saved to " + reportPath +
                                               "\nWould you like to submit it to JetBrains?",
                                      "Submit Performance Report",
                                      Messages.getInformationIcon());
    if (rc == 0) {
      ProgressManager.getInstance().run(new Task.Backgroundable(project, "Uploading Performance Report") {
        public void run(@NotNull final ProgressIndicator indicator) {
          final String error = uploadFileToFTP(reportPath, "ftp.intellij.net", ".uploads", indicator);
          if (error != null) {
            ApplicationManager.getApplication().invokeLater(new Runnable() {
              public void run() {
                Messages.showErrorDialog(project, error, "Submit Performance Report");
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
    try {
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
        return "FTP server refused connection";
      }
      if (!ftp.changeWorkingDirectory(directory)) {
        return "Failed to change directory";
      }
      FileInputStream readStream = new FileInputStream(reportPath);
      try {
        if (!ftp.storeFile(reportPath.getName(), readStream)) {
          return "Failed to upload file";
        }
      }
      finally {
        readStream.close();
      }
      ftp.logout();
    }
    catch (IOException e) {
      return e.getMessage();
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
    return null;
  }
}
