package com.intellij.diagnostic;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.project.Project;
import com.intellij.util.SystemProperties;
import com.intellij.util.io.ZipUtil;

import java.io.File;
import java.io.FileFilter;
import java.io.FileOutputStream;
import java.io.IOException;
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
    Messages.showMessageDialog(project, "The performance report has been saved to " + reportPath, "Submit Performance Report",
                               Messages.getInformationIcon());
  }
}
