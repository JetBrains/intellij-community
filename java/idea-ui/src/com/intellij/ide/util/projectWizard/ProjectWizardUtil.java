// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.util.projectWizard;

import com.intellij.CommonBundle;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.JavaUiBundle;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkTypeId;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.roots.ui.configuration.JdkComboBox;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.SystemInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
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
    else {
      return Files.isWritable(dir);
    }
  }

  public static void preselectJdkForNewModule(@Nullable Project project,
                                              @Nullable String lastUsedSdk,
                                              @NotNull JdkComboBox jdkComboBox,
                                              @NotNull ModuleBuilder moduleBuilder,
                                              @NotNull Condition<? super SdkTypeId> sdkFilter) {
    jdkComboBox.reloadModel();

    if (project != null) {
      Sdk sdk = ProjectRootManager.getInstance(project).getProjectSdk();
      if (sdk != null && moduleBuilder.isSuitableSdkType(sdk.getSdkType())) {
        // use project SDK
        jdkComboBox.setSelectedItem(jdkComboBox.showProjectSdkItem());
        return;
      }
    }

    if (lastUsedSdk != null) {
      Sdk sdk = ProjectJdkTable.getInstance().findJdk(lastUsedSdk);
      if (sdk != null && moduleBuilder.isSuitableSdkType(sdk.getSdkType())) {
        jdkComboBox.setSelectedJdk(sdk);
        return;
      }
    }

    // set default project SDK
    Project defaultProject = ProjectManager.getInstance().getDefaultProject();
    Sdk selected = ProjectRootManager.getInstance(defaultProject).getProjectSdk();
    if (selected != null && sdkFilter.value(selected.getSdkType())) {
      jdkComboBox.setSelectedJdk(selected);
      return;
    }

    Sdk best = null;
    ComboBoxModel<JdkComboBox.JdkComboBoxItem> model = jdkComboBox.getModel();
    for (int i = 0; i < model.getSize(); i++) {
      JdkComboBox.JdkComboBoxItem item = model.getElementAt(i);
      if (!(item instanceof JdkComboBox.ActualJdkComboBoxItem)) continue;

      Sdk jdk = item.getJdk();
      if (jdk == null) continue;

      SdkTypeId jdkType = jdk.getSdkType();
      if (!sdkFilter.value(jdkType)) continue;

      if (best == null) {
        best = jdk;
        continue;
      }

      SdkTypeId bestType = best.getSdkType();
      //it is in theory possible to have several SDK types here, let's just pick the first lucky type for now
      if (bestType == jdkType && bestType.versionComparator().compare(best, jdk) < 0) {
        best = jdk;
      }
    }

    if (best != null) {
      jdkComboBox.setSelectedJdk(best);
    }
    else {
      jdkComboBox.setSelectedItem(jdkComboBox.showNoneSdkItem());
    }
  }
}