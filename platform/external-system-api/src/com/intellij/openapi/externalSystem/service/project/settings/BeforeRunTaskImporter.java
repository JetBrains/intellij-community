// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.service.project.settings;

import com.intellij.execution.BeforeRunTask;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.externalSystem.model.ProjectKeys;
import com.intellij.openapi.externalSystem.model.project.settings.ConfigurationData;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

/**
 * Implementations of this interface are supposed to create/update before run tasks for run configurations
 * based on information obtained from external build tool (e.g., Gradle).
 *
 * @see RunConfigurationImporter
 */
public interface BeforeRunTaskImporter {
  ExtensionPointName<BeforeRunTaskImporter> EP_NAME = ExtensionPointName.create("com.intellij.externalSystem.beforeRunTaskImporter");

  /**
   * Checks that this importer can create/update before run tasks for configuration with given type.
   * Possible type names are specified only by reply part on external build tool side.
   *
   * @param typeName is before run task type name.
   * @see ConfigurationData
   * @see ProjectKeys#CONFIGURATION
   */
  boolean canImport(@NotNull String typeName);

  /**
   * Given a map of before run configuration settings,
   * optionally create relevant BeforeRunTask if it is missing from beforeRunTasks list.
   *
   * @param project           is a project into which this run configuration will be added.
   * @param modelsProvider    is a IDE project structure modifiable model.
   * @param runConfiguration  is a run configuration before which before run tasks will be executed.
   * @param beforeRunTasks    is a list of before run tasks which are already processed by other importers for {@code runConfiguration}.
   * @param configurationData is a map of before run configuration settings.
   * @return updated {@code beforeRunTasks} list.
   */
  List<BeforeRunTask> process(
    @NotNull Project project,
    @NotNull IdeModifiableModelsProvider modelsProvider,
    @NotNull RunConfiguration runConfiguration,
    @NotNull List<BeforeRunTask> beforeRunTasks,
    @NotNull Map<String, Object> configurationData);
}
