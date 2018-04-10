// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.service.project.settings;

import com.intellij.execution.BeforeRunTask;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

public interface BeforeRunTaskImporter {
  ExtensionPointName<BeforeRunTaskImporter> EP_NAME = ExtensionPointName.create("com.intellij.externalSystem.beforeRunTaskImporter");

  List<BeforeRunTask> process(@NotNull Project project,
               @NotNull IdeModifiableModelsProvider modelsProvider,
               @NotNull RunConfiguration runConfiguration,
               @NotNull List<BeforeRunTask> beforeRunTasks,
               @NotNull Map<String, Object> cfg);

  boolean canImport(@NotNull String typeName);
}
