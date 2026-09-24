// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution;

import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Provides {@link ExecutionTarget ExecutionTargets} for run configurations.
 */
public abstract class ExecutionTargetProvider {
  public static final ExtensionPointName<ExecutionTargetProvider> EXTENSION_NAME =
    ExtensionPointName.create("com.intellij.executionTargetProvider");

  public abstract List<ExecutionTarget> getTargets(@NotNull Project project, @NotNull RunConfiguration configuration);
}
