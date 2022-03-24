// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution;

import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;

/**
 * Provides {@link ExecutionTarget ExecutionTargets} for run configurations.
 */
public abstract class ExecutionTargetProvider {
  public static final ExtensionPointName<ExecutionTargetProvider> EXTENSION_NAME =
    ExtensionPointName.create("com.intellij.executionTargetProvider");

  /**
   * @deprecated use {@link #getTargets(Project, RunConfiguration)} instead
   */
  @NotNull
  @Deprecated(forRemoval = true)
  public List<ExecutionTarget> getTargets(@NotNull Project project, @NotNull RunnerAndConfigurationSettings configuration) {
    throw new AbstractMethodError();
  }

  public List<ExecutionTarget> getTargets(@NotNull Project project, @NotNull RunConfiguration configuration) {
    return getTargets(project, Objects.requireNonNull(RunManager.getInstance(project).findSettings(configuration)));
  }
}
