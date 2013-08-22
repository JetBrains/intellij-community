/*
 * Copyright 2013 JetBrains s.r.o.
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
package com.intellij.openapi.roots.ui.configuration;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public abstract class ProjectStructureConfigurableFilter {
  public static final ExtensionPointName<ProjectStructureConfigurableFilter> EP_NAME = ExtensionPointName.create("com.intellij.projectStructureConfigurableFilter");

  public enum ConfigurableId {
    PROJECT, MODULES, PROJECT_LIBRARIES, FACETS, ARTIFACTS, JDK_LIST, GLOBAL_LIBRARIES
  }

  public abstract boolean isAvailable(@NotNull ConfigurableId id, @NotNull Project project);
}
