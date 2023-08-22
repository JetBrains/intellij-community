// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.project.ex;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;

public interface ProjectNameProvider {
  ExtensionPointName<ProjectNameProvider> EP_NAME = new ExtensionPointName<>("com.intellij.projectNameProvider");

  /**
   * @deprecated use {@link ProjectEx#setProjectName(String)} to set custom project name instead. 
   * This method is called during initialization of the project, and if its implementation uses not fully initialized {@link Project} 
   * instance passed as a parameter, it may lead to problems. So the platform doesn't call this method anymore.
   */
  @Deprecated(forRemoval = true)
  default @Nullable String getDefaultName(@NotNull Project project) {
    return null;
  }

  @ApiStatus.Experimental
  default @Nullable Path getNameFile(@NotNull Project project) { return null; }
}
