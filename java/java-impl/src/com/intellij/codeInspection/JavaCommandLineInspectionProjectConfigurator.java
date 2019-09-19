// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection;

import com.intellij.analysis.AnalysisScope;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * @author yole
 */
public class JavaCommandLineInspectionProjectConfigurator implements CommandLineInspectionProjectConfigurator {
  @Override
  public boolean isApplicable(Path projectPath) {
    try {
      return Files.walk(projectPath).anyMatch(f -> f.toString().endsWith(".java"));
    }
    catch (IOException e) {
      return false;
    }
  }

  @Override
  public void configureEnvironment() {
    JavaSdk javaSdk = JavaSdk.getInstance();
    List<Sdk> sdks = ProjectJdkTable.getInstance().getSdksOfType(javaSdk);
    if (sdks.isEmpty()) {
      Collection<String> homePaths = javaSdk.suggestHomePaths();
      Set<JavaSdkVersion> existingVersions = EnumSet.noneOf(JavaSdkVersion.class);
      for (String path : homePaths) {
        String jdkVersion = javaSdk.getVersionString(path);
        if (jdkVersion == null) continue;
        JavaSdkVersion version = JavaSdkVersion.fromVersionString(jdkVersion);
        if (existingVersions.contains(version)) continue;
        existingVersions.add(version);
        String name = javaSdk.suggestSdkName(null, path);
        Sdk jdk = javaSdk.createJdk(name, path, false);
        ApplicationManager.getApplication().runWriteAction(() ->  ProjectJdkTable.getInstance().addJdk(jdk));
      }
    }
  }


  @Override
  public void configureProject(@NotNull Project project, AnalysisScope scope) {
  }
}
