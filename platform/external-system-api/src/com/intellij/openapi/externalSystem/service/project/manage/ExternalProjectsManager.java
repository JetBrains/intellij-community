// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.service.project.manage;

import com.intellij.openapi.externalSystem.importing.ImportSpec;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.service.project.autoimport.ExternalSystemProjectsWatcher;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/**
 * @author Vladislav.Soroka
 */
public interface ExternalProjectsManager {

  static ExternalProjectsManager getInstance(@NotNull Project project) {
    return project.getService(ExternalProjectsManager.class);
  }

  @NotNull
  Project getProject();

  ExternalSystemProjectsWatcher getExternalProjectsWatcher();

  void refreshProject(@NotNull String externalProjectPath, @NotNull ImportSpec importSpec);

  /**
   * Execute runnable after External projects manager is fully initialized.
   * <p>
   * Initialization includes loading external project data cache and can take visible time,
   * during which query to {@link com.intellij.openapi.externalSystem.util.ExternalSystemUtil#getExternalProjectInfo(Project, ProjectSystemId, String) ExternalSystemUtil#getExternalProjectInfo}
   * will return null. Use this method to postpone such queries.
   *
   */
  void runWhenInitialized(Runnable runnable);

  boolean isIgnored(@NotNull ProjectSystemId systemId, @NotNull String projectPath);

  void setIgnored(@NotNull DataNode<?> dataNode, boolean isIgnored);
}
