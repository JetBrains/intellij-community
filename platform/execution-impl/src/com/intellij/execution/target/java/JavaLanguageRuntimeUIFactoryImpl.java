// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.target.java;

import com.intellij.execution.target.TargetEnvironmentConfiguration;
import com.intellij.execution.target.TargetEnvironmentType;
import com.intellij.openapi.options.BoundConfigurable;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.function.Supplier;

@ApiStatus.Internal
public final class JavaLanguageRuntimeUIFactoryImpl implements JavaLanguageRuntimeUIFactory {

  @Override
  public @NotNull BoundConfigurable create(@NotNull JavaLanguageRuntimeConfiguration config,
                                           @NotNull TargetEnvironmentType<? extends TargetEnvironmentConfiguration> targetType,
                                           @NotNull Supplier<? extends TargetEnvironmentConfiguration> targetSupplier,
                                           @NotNull Project project) {
    return new JavaLanguageRuntimeUI(config, targetType, targetSupplier, project);
  }
}