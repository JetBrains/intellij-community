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
package com.intellij.ide.util.projectWizard;

import com.intellij.CommonBundle;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;

import java.io.File;

/**
 * @author cdr
 */
public class ProjectWizardUtil {
  private ProjectWizardUtil() { }

  public static String findNonExistingFileName(String searchDirectory, String preferredName, String extension) {
    for (int idx = 0; ; idx++) {
      String fileName = (idx > 0 ? preferredName + idx : preferredName) + extension;
      if (!new File(searchDirectory, fileName).exists()) {
        return fileName;
      }
    }
  }

  public static boolean createDirectoryIfNotExists(String promptPrefix, String directoryPath, boolean promptUser) {
    File dir = new File(directoryPath);

    if (!dir.exists()) {
      if (promptUser) {
        String ide = ApplicationNamesInfo.getInstance().getFullProductName();
        String message = IdeBundle.message("prompt.project.wizard.directory.does.not.exist", promptPrefix, dir, ide);
        int answer = Messages.showOkCancelDialog(message, IdeBundle.message("title.directory.does.not.exist"), Messages.getQuestionIcon());
        if (answer != Messages.OK) {
          return false;
        }
      }

      if (!FileUtil.createDirectory(dir)) {
        Messages.showErrorDialog(IdeBundle.message("error.failed.to.create.directory", dir.getPath()), CommonBundle.getErrorTitle());
        return false;
      }
    }

    if (SystemInfo.isUnix && !dir.canWrite()) {
      Messages.showErrorDialog(IdeBundle.message("error.directory.read.only", dir.getPath()), CommonBundle.getErrorTitle());
      return false;
    }

    return true;
  }
}