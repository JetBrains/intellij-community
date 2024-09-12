// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.incremental.relativizer;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;
import org.jetbrains.jps.model.serialization.JpsMavenSettings;

@VisibleForTesting
@ApiStatus.Internal
public final class MavenPathRelativizer extends CommonPathRelativizer {
  private static final String IDENTIFIER = "$MAVEN_REPOSITORY$";

  public MavenPathRelativizer(@NotNull String mavenRepositoryPath) {
    super(mavenRepositoryPath, IDENTIFIER);
  }

  public static @Nullable String getNormalizedMavenRepositoryPath() {
    String path = JpsMavenSettings.getMavenRepositoryPath();
    return path == null ? null : PathRelativizerService.normalizePath(path);
  }
}
