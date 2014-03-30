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
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ui.configuration.projectRoot.StructureConfigurableContext;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

public abstract class ProjectStructureConfigurableContributor {
  public static final ExtensionPointName<ProjectStructureConfigurableContributor> EP_NAME = ExtensionPointName.create("com.intellij.projectStructureConfigurableAdder");

  @NotNull
  public List<? extends Configurable> getExtraProjectConfigurables(@NotNull Project project, @NotNull StructureConfigurableContext context) {
    return Collections.emptyList();
  }

  @NotNull
  public List<? extends Configurable> getExtraPlatformConfigurables(@NotNull Project project, @NotNull StructureConfigurableContext context) {
    return Collections.emptyList();
  }

}
