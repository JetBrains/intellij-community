// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.model.jarRepository.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.JpsProject;
import org.jetbrains.jps.model.jarRepository.JpsRemoteRepositoriesConfiguration;
import org.jetbrains.jps.model.jarRepository.JpsRemoteRepositoryService;

/**
 * @author Eugene Zhuravlev
 */
public class JpsRemoteRepositoryServiceImpl extends JpsRemoteRepositoryService {
  @Override
  public @Nullable JpsRemoteRepositoriesConfiguration getRemoteRepositoriesConfiguration(@NotNull JpsProject project) {
    return project.getContainer().getChild(JpsRemoteRepositoriesConfigurationImpl.ROLE);
  }

  @Override
  public synchronized @NotNull JpsRemoteRepositoriesConfiguration getOrCreateRemoteRepositoriesConfiguration(@NotNull JpsProject project) {
    JpsRemoteRepositoriesConfiguration config = getRemoteRepositoriesConfiguration(project);
    if (config == null) {
      config = project.getContainer().setChild(JpsRemoteRepositoriesConfigurationImpl.ROLE, new JpsRemoteRepositoriesConfigurationImpl());
    }
    return config;
  }
}
