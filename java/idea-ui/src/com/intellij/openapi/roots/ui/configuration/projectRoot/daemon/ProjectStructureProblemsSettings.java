// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.roots.ui.configuration.projectRoot.daemon;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public abstract class ProjectStructureProblemsSettings {
  public static ProjectStructureProblemsSettings getProjectInstance(@NotNull Project project) {
    return ServiceManager.getService(project, ProjectStructureProblemsSettings.class);
  }

  public static ProjectStructureProblemsSettings getGlobalInstance() {
    return ApplicationManager.getApplication().getService(ProjectStructureProblemsSettings.class);
  }

  public abstract boolean isIgnored(@NotNull ProjectStructureProblemDescription description);
  public abstract void setIgnored(@NotNull ProjectStructureProblemDescription description, boolean ignored);
}
