/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.openapi.externalSystem.service.project.settings;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.externalSystem.model.ProjectKeys;
import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.externalSystem.model.project.settings.ConfigurationData;
import com.intellij.openapi.externalSystem.service.project.IdeModelsProvider;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Handles and applies custom configuration data that defines on build tool side.
 * For example, we can define run configuration in build scripts and put this run configuration data into {@link ConfigurationData}.
 * On IDE side we describes {@link ConfigurationHandler} which processes this data and creates run configuration.
 * Reply part already implemented by com.intellij.openapi.externalSystem.service.project.settings.RunConfigurationHandler.
 * <p>
 * Note: {@link ConfigurationData}'s structure is unspecified. So it can keep any serializable data which can help update IDE model.
 *
 * @see ProjectKeys#CONFIGURATION
 */
@ApiStatus.Experimental
public interface ConfigurationHandler {
  ExtensionPointName<ConfigurationHandler> EP_NAME = ExtensionPointName.create("com.intellij.externalSystemConfigurationHandler");

  /**
   * @see ConfigurationHandler#apply(Project, ProjectData, IdeModifiableModelsProvider, ConfigurationData)
   */
  default void apply(@NotNull Project project,
                     @NotNull IdeModifiableModelsProvider modelsProvider,
                     @NotNull ConfigurationData configuration) { }

  /**
   * Applies custom configuration into project wide IDE model.
   *
   * @param project        is a project level service container.
   * @param projectData    is a common project data that was collected on build tool side.
   * @param modelsProvider is a IDE modifiable model to apply custom configurations.
   * @param configuration  is a configurations to apply.
   */
  default void apply(@NotNull Project project,
                     @Nullable ProjectData projectData,
                     @NotNull IdeModifiableModelsProvider modelsProvider,
                     @NotNull ConfigurationData configuration) {
    apply(project, modelsProvider, configuration);
  }

  /**
   * Applies custom configuration into module wide IDE model.
   *
   * @param module         is a module level service container.
   * @param modelsProvider is a IDE modifiable model to apply custom configurations.
   * @param configuration  is a configurations to apply.
   */
  default void apply(@NotNull Module module,
                     @NotNull IdeModifiableModelsProvider modelsProvider,
                     @NotNull ConfigurationData configuration) { }

  /**
   * Configures the <code>project</code> after a successful project import.
   */
  default void onSuccessImport(@NotNull Project project,
                               @Nullable ProjectData projectData,
                               @NotNull IdeModelsProvider modelsProvider,
                               @NotNull ConfigurationData configuration) {}

  /**
   * Configures the <code>module</code> after a successful project import.
   */
  default void onSuccessImport(@NotNull Module module,
                               @NotNull IdeModelsProvider modelsProvider,
                               @NotNull ConfigurationData configuration) {}
}
