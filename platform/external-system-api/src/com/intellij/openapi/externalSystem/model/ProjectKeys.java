// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.model;

import com.intellij.openapi.externalSystem.model.project.*;
import com.intellij.openapi.externalSystem.model.project.dependencies.ProjectDependencies;
import com.intellij.openapi.externalSystem.model.project.settings.ConfigurationData;
import com.intellij.openapi.externalSystem.model.task.TaskData;
import org.jetbrains.annotations.NotNull;

/**
 * Holds common project entity {@link Key keys}.
 */
public final class ProjectKeys {

  public static final @NotNull Key<ModuleData>            MODULE             = Key.create(ModuleData.class, 50);
  public static final @NotNull Key<ProjectData>           PROJECT            = Key.create(ProjectData.class, 70);
  public static final @NotNull Key<LibraryData>           LIBRARY            = Key.create(LibraryData.class, 90);
  public static final @NotNull Key<ContentRootData>       CONTENT_ROOT       = Key.create(ContentRootData.class, 110);
  public static final @NotNull Key<ModuleDependencyData>  MODULE_DEPENDENCY  = Key.create(ModuleDependencyData.class, 130);
  public static final @NotNull Key<LibraryDependencyData> LIBRARY_DEPENDENCY = Key.create(LibraryDependencyData.class, 150);

  public static final @NotNull Key<TaskData>              TASK = Key.create(TaskData.class, 250);

  public static final @NotNull Key<ConfigurationData>     CONFIGURATION = Key.create(ConfigurationData.class, 350);

  public static final @NotNull Key<TestData>              TEST = Key.create(TestData.class, 450);

  public static final @NotNull Key<ProjectDependencies>   DEPENDENCIES_GRAPH = Key.create(ProjectDependencies.class, 500);

  private ProjectKeys() {
  }
}
