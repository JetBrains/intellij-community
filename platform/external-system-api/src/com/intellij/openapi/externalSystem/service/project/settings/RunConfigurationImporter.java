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

import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.externalSystem.model.ProjectKeys;
import com.intellij.openapi.externalSystem.model.project.settings.ConfigurationData;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * Implementations of this interface are supposed to create/update run configurations based on information obtained from
 * external build tool (e.g., Gradle)
 */
public interface RunConfigurationImporter {
  ExtensionPointName<RunConfigurationImporter> EP_NAME = ExtensionPointName.create("com.intellij.externalSystem.runConfigurationImporter");

  /**
   * Checks that this importer can create/update run configuration with given type.
   * Possible type names are specified only by reply part on external build tool side.
   *
   * @param typeName is run configuration type name.
   * @see ConfigurationData
   * @see ProjectKeys#CONFIGURATION
   */
  boolean canImport(@NotNull String typeName);

  /**
   * Provides run configuration factory for creating instance run configuration with predefined configuration type name.
   *
   * @see RunConfigurationImporter#canImport
   */
  @NotNull ConfigurationFactory getConfigurationFactory();

  /**
   * Given a map of configuration settings,
   * optionally create relevant BeforeRunTask if it is missing from beforeRunTasks list.
   *
   * @param project          is a project into which this run configuration will be added.
   * @param modelsProvider   is a IDE project structure modifiable model.
   * @param runConfiguration is a run configuration to process.
   * @param cfg              is a map of configuration settings.
   */
  void process(
    @NotNull Project project,
    @NotNull RunConfiguration runConfiguration,
    @NotNull Map<String, Object> cfg,
    @NotNull IdeModifiableModelsProvider modelsProvider
  );
}