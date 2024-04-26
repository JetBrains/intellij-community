// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compiler.cache.ui;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurableProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.registry.Registry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class CompilerCacheConfigurableProvider extends ConfigurableProvider {
  @NotNull
  private final Project myProject;

  public CompilerCacheConfigurableProvider(@NotNull Project project) {
    myProject = project;
  }

  @Override
  public @Nullable Configurable createConfigurable() {
    return new CompilerCacheConfigurable(myProject);
  }

  @Override
  public boolean canCreateConfigurable() {
    return Registry.is("compiler.process.use.portable.caches");
  }
}
