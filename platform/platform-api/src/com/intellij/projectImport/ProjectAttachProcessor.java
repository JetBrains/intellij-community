// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.projectImport;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;

/**
 * @author yole
 */
public class ProjectAttachProcessor {
  public static final ExtensionPointName<ProjectAttachProcessor> EP_NAME = new ExtensionPointName<>("com.intellij.projectAttachProcessor");

  /**
   * Called to attach the directory projectDir as a module to the specified project.
   *
   * @param project    the project to attach the directory to.
   * @param projectDir the directory to attach.
   * @param callback   the callback to call on successful attachment
   * @return true if the attach succeeded, false if the project should be opened in a new window.
   */
  public boolean attachToProject(Project project, @NotNull Path projectDir, @Nullable ProjectOpenedCallback callback) {
    return false;
  }

  public void beforeDetach(@NotNull Module module) {}

  public static boolean canAttachToProject() {
    return EP_NAME.getPoint(null).hasAnyExtensions();
  }
}
