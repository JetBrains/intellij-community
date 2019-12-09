// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * @author yole
 */
public class JavaCommandLineInspectionProjectConfigurator implements CommandLineInspectionProjectConfigurator {
  @Override
  public boolean isApplicable(@NotNull Path projectPath, @NotNull CommandLineInspectionProgressReporter logger) {
    JavaSdk javaSdk = JavaSdk.getInstance();
    List<Sdk> sdks = ProjectJdkTable.getInstance().getSdksOfType(javaSdk);
    if (!sdks.isEmpty()) return false;

    try {
      boolean hasAnyJavaFiles = Files.walk(projectPath).anyMatch(f -> f.toString().endsWith(".java"));
      if (!hasAnyJavaFiles) {
        logger.reportMessage(3, "Skipping JDK autodetection because the project doesn't contain any Java files");
      }
      return hasAnyJavaFiles;
    }
    catch (IOException e) {
      return false;
    }
  }

  @Override
  public void configureEnvironment(@NotNull Path projectPath, @NotNull CommandLineInspectionProgressReporter logger) {
    JavaSdk javaSdk = JavaSdk.getInstance();
    List<Sdk> sdks = ProjectJdkTable.getInstance().getSdksOfType(javaSdk);
    if (!sdks.isEmpty()) {
      return;
    }

    Collection<String> homePaths = javaSdk.suggestHomePaths();
    Set<JavaSdkVersion> existingVersions = EnumSet.noneOf(JavaSdkVersion.class);
    for (String path : homePaths) {
      String jdkVersion = javaSdk.getVersionString(path);
      if (jdkVersion == null) continue;
      JavaSdkVersion version = JavaSdkVersion.fromVersionString(jdkVersion);
      if (existingVersions.contains(version)) continue;
      existingVersions.add(version);
      String name = javaSdk.suggestSdkName(null, path);
      logger.reportMessage(2, "Detected JDK with name " + name + " at " + path);
      Sdk jdk = javaSdk.createJdk(name, path, false);
      ApplicationManager.getApplication().runWriteAction(() ->  ProjectJdkTable.getInstance().addJdk(jdk));
    }
  }
}
