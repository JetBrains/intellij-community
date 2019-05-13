// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.compiler.options;

import com.intellij.execution.BeforeRunTask;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.externalSystem.service.project.settings.BeforeRunTaskImporter;
import com.intellij.openapi.project.Project;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

public class CompileStepBeforeRunImporter implements BeforeRunTaskImporter {
  @Override
  public List<BeforeRunTask> process(@NotNull Project project,
                                     @NotNull IdeModifiableModelsProvider modelsProvider,
                                     @NotNull RunConfiguration runConfiguration,
                                     @NotNull List<BeforeRunTask> beforeRunTasks,
                                     @NotNull Map<String, Object> cfg) {
    ObjectUtils.consumeIfCast(cfg.get("enabled"), Boolean.class, (enabled) -> {
      if (!enabled) {
        beforeRunTasks.removeIf((it) -> it.getProviderId() == CompileStepBeforeRun.ID);
      }
    });
    return beforeRunTasks;
  }

  @Override
  public boolean canImport(@NotNull String typeName) {
    return "make".equals(typeName);
  }
}
