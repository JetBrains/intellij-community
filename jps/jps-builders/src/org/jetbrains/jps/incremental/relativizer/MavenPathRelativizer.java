// Copyright 2000-2023 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.incremental.relativizer;

import org.jetbrains.annotations.VisibleForTesting;
import org.jetbrains.jps.model.serialization.JpsMavenSettings;

@VisibleForTesting
public class MavenPathRelativizer extends SubPathRelativizer {
  private static final String IDENTIFIER = "$MAVEN_REPOSITORY$";

  public MavenPathRelativizer() {
    super(getNormalizedMavenRepositoryPath(), IDENTIFIER);
  }

  private static String getNormalizedMavenRepositoryPath() {
    String path = JpsMavenSettings.getMavenRepositoryPath();
    if (path != null) {
      return PathRelativizerService.normalizePath(path);
    }
    return null;
  }
}
