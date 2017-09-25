/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.externalSystem.model;

import com.intellij.openapi.externalSystem.model.project.*;
import com.intellij.openapi.externalSystem.model.task.TaskData;
import org.jetbrains.annotations.NotNull;

/**
 * Holds common project entity {@link Key keys}.
 */
public class ProjectKeys {

  @NotNull public static final Key<ModuleData>            MODULE             = Key.create(ModuleData.class, 50);
  @NotNull public static final Key<ProjectData>           PROJECT            = Key.create(ProjectData.class, 70);
  @NotNull public static final Key<LibraryData>           LIBRARY            = Key.create(LibraryData.class, 90);
  @NotNull public static final Key<ContentRootData>       CONTENT_ROOT       = Key.create(ContentRootData.class, 110);
  @NotNull public static final Key<ModuleDependencyData>  MODULE_DEPENDENCY  = Key.create(ModuleDependencyData.class, 130);
  @NotNull public static final Key<LibraryDependencyData> LIBRARY_DEPENDENCY = Key.create(LibraryDependencyData.class, 150);

  @NotNull public static final Key<TaskData>              TASK = Key.create(TaskData.class, 250);

  @NotNull public static final Key<ConfigurationData>     CONFIGURATION = Key.create(ConfigurationData.class, 350);

  private ProjectKeys() {
  }
}
