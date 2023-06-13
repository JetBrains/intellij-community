// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.jps.model.resolver;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.JpsProject;
import org.jetbrains.jps.service.JpsServiceManager;

public abstract class JpsDependencyResolverConfigurationService {
  public static JpsDependencyResolverConfigurationService getInstance() {
    return JpsServiceManager.getInstance().getService(JpsDependencyResolverConfigurationService.class);
  }

  @Nullable
  public abstract JpsDependencyResolverConfiguration getDependencyResolverConfiguration(@NotNull JpsProject project);

  @NotNull
  public abstract JpsDependencyResolverConfiguration getOrCreateDependencyResolverConfiguration(@NotNull JpsProject project);
}
