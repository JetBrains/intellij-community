// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

  @NotNull public static final Key<ModuleData>            MODULE             = Key.create(ModuleData.class, 50);
  @NotNull public static final Key<ProjectData>           PROJECT            = Key.create(ProjectData.class, 70);
  @NotNull public static final Key<LibraryData>           LIBRARY            = Key.create(LibraryData.class, 90);
  @NotNull public static final Key<ContentRootData>       CONTENT_ROOT       = Key.create(ContentRootData.class, 110);
  @NotNull public static final Key<ModuleDependencyData>  MODULE_DEPENDENCY  = Key.create(ModuleDependencyData.class, 130);
  @NotNull public static final Key<LibraryDependencyData> LIBRARY_DEPENDENCY = Key.create(LibraryDependencyData.class, 150);

  @NotNull public static final Key<TaskData>              TASK = Key.create(TaskData.class, 250);

  @NotNull public static final Key<ConfigurationData>     CONFIGURATION = Key.create(ConfigurationData.class, 350);

  @NotNull public static final Key<TestData>              TEST = Key.create(TestData.class, 450);

  @NotNull public static final Key<ProjectDependencies>   DEPENDENCIES_GRAPH = Key.create(ProjectDependencies.class, 500);

  private ProjectKeys() {
  }
}
