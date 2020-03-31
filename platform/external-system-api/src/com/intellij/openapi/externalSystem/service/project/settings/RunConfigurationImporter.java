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

  void process(@NotNull Project project,
                       @NotNull RunConfiguration runConfiguration,
                       @NotNull Map<String, Object> cfg,
                       @NotNull IdeModifiableModelsProvider modelsProvider);
  boolean canImport(@NotNull String typeName);
  @NotNull ConfigurationFactory getConfigurationFactory();
}