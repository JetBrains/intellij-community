// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.jps.model.impl.resolver;

import com.intellij.platform.jps.model.resolver.JpsDependencyResolverConfiguration;
import com.intellij.platform.jps.model.resolver.JpsDependencyResolverConfigurationService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.JpsProject;

public class JpsDependencyResolverConfigurationServiceImpl extends JpsDependencyResolverConfigurationService {
  @Override
  public @Nullable JpsDependencyResolverConfiguration getDependencyResolverConfiguration(@NotNull JpsProject project) {
    return project.getContainer().getChild(JpsDependencyResolverConfigurationImpl.ROLE);
  }

  @Override
  public @NotNull JpsDependencyResolverConfiguration getOrCreateDependencyResolverConfiguration(@NotNull JpsProject project) {
    JpsDependencyResolverConfiguration config = getDependencyResolverConfiguration(project);
    if (config == null) {
      config = project.getContainer().setChild(JpsDependencyResolverConfigurationImpl.ROLE, new JpsDependencyResolverConfigurationImpl());
    }
    return config;
  }
}
