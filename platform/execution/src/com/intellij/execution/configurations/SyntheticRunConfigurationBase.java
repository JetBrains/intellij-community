// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.configurations;

import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Base class for run configurations that do not allow editing
 */
public abstract class SyntheticRunConfigurationBase<T> extends RunConfigurationBase<T> {
  protected SyntheticRunConfigurationBase(@NotNull Project project,
                                          @Nullable ConfigurationFactory factory,
                                          @Nullable String name) {
    super(project, factory, name);
  }

  @Override
  public final @NotNull SettingsEditor<? extends RunConfiguration> getConfigurationEditor() {
    throw new IllegalStateException("Must not be invoked");
  }
}
