// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.model.jarRepository;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.JpsProject;
import org.jetbrains.jps.service.JpsServiceManager;

/**
 * @author Eugene Zhuravlev
 */
public abstract class JpsRemoteRepositoryService {
  public static JpsRemoteRepositoryService getInstance() {
    return JpsServiceManager.getInstance().getService(JpsRemoteRepositoryService.class);
  }

  public abstract @Nullable JpsRemoteRepositoriesConfiguration getRemoteRepositoriesConfiguration(@NotNull JpsProject project);

  public abstract @NotNull JpsRemoteRepositoriesConfiguration getOrCreateRemoteRepositoriesConfiguration(@NotNull JpsProject project);
}
