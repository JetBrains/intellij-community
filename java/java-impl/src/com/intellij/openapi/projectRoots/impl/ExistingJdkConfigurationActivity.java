// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.projectRoots.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PreloadingActivity;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.JdkFinder;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.registry.Registry;

import java.util.ArrayList;
import java.util.List;

import static com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil.createAndAddSDK;

/**
 * This {@link PreloadingActivity} makes it possible to index an installed JDK without any
 * configuration.
 * The registry key <code>jdk.configure.existing</code> must be enabled.
 *
 * <ul>
 * <li> Automatically registers JDKs on the computer in the {@link ProjectJdkTable}</li>
 * <li> Uses the first JDK found as project SDK if none was configured</li>
 * </ul>
 */
public class ExistingJdkConfigurationActivity extends PreloadingActivity {

  @Override
  public void preload() {
    if (!Registry.is("jdk.configure.existing", false)) return;

    final var javaSdk = JavaSdk.getInstance();
    final List<Sdk> registeredJdks = ProjectJdkTable.getInstance().getSdksOfType(javaSdk);
    if (!registeredJdks.isEmpty()) return;

    final ArrayList<String> jdkPathsToAdd = new ArrayList<>();

    // Collect JDKs to register
    JdkFinder.getInstance().suggestHomePaths().forEach(homePath -> {
      if (javaSdk.isValidSdkHome(homePath)) {
        jdkPathsToAdd.add(homePath);
      }
    });

    ApplicationManager.getApplication().invokeLater(() -> {
      Sdk added = null;

      // Register collected JDKs
      for (String path : jdkPathsToAdd) {
        final Sdk newSdk = ApplicationManager.getApplication().runWriteAction(
          (Computable<Sdk>)() -> createAndAddSDK(path, javaSdk)
        );
        if (added == null) added = newSdk;
      }

      // Configure missing project JDKs
      if (added != null) {
        final Sdk finalAdded = added;
        for (Project project : ProjectManager.getInstance().getOpenProjects()) {
          final var rootManager = ProjectRootManager.getInstance(project);
          if (rootManager.getProjectSdk() == null) {
            ApplicationManager.getApplication().runWriteAction(() -> rootManager.setProjectSdk(finalAdded));
          }
        }
      }
    });
  }
}
