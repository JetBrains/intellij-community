// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.externalDependencies.impl;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurableProvider;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class ExternalDependenciesConfigurableProvider extends ConfigurableProvider {
  private final Project myProject;

  public ExternalDependenciesConfigurableProvider(Project project) {
    myProject = project;
  }

  @Override
  public @Nullable Configurable createConfigurable() {
    return new ExternalDependenciesConfigurable(myProject);
  }
}
