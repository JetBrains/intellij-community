// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.util.projectWizard;

import com.intellij.CommonBundle;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.JavaUiBundle;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.SystemInfo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public final class ProjectWizardUtil {
  private ProjectWizardUtil() { }

  public static String findNonExistingFileName(String searchDirectory, String preferredName, String extension) {
    for (int idx = 0; ; idx++) {
      String fileName = (idx > 0 ? preferredName + idx : preferredName) + extension;
      if (!Files.exists(Paths.get(searchDirectory, fileName))) {
        return fileName;
      }
    }
  }

  public static boolean createDirectoryIfNotExists(String promptPrefix, String directoryPath, boolean promptUser) {
    Path dir = Paths.get(directoryPath);

    if (!Files.exists(dir)) {
      if (promptUser) {
        String ide = ApplicationNamesInfo.getInstance().getFullProductName();
        String message = JavaUiBundle.message("prompt.project.wizard.directory.does.not.exist", promptPrefix, dir, ide);
        int answer = Messages.showOkCancelDialog(message, JavaUiBundle.message("title.directory.does.not.exist"), IdeBundle.message("button.create"), IdeBundle.message("button.cancel"), Messages.getQuestionIcon());
        if (answer != Messages.OK) {
          return false;
        }
      }

      try {
        Files.createDirectories(dir);
      }
      catch (IOException e) {
        Logger.getInstance(ProjectWizardUtil.class).warn(e);
        Messages.showErrorDialog(IdeBundle.message("error.failed.to.create.directory", dir), CommonBundle.getErrorTitle());
        return false;
      }
    }

    if (!isWritable(dir)) {
      Messages.showErrorDialog(JavaUiBundle.message("error.directory.read.only", dir), CommonBundle.getErrorTitle());
      return false;
    }

    return true;
  }

  private static boolean isWritable(Path dir) {
    if (SystemInfo.isWindows) {
      try {
        Files.deleteIfExists(Files.createTempFile(dir, "probe_", ".txt"));
        return true;
      }
      catch (IOException e) {
        Logger.getInstance(ProjectWizardUtil.class).debug(e);
        return false;
      }
    }
    else  {
      return Files.isWritable(dir);
    }
  }
}