// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.incremental.relativizer;

import com.intellij.util.SystemProperties;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

final class GradlePathRelativizer extends CommonPathRelativizer {
  private static final String IDENTIFIER = "$GRADLE_REPOSITORY$";
  private static final String GRADLE_USER_HOME = "GRADLE_USER_HOME";

  GradlePathRelativizer(@NotNull String gradleRepositoryPath) {
    super(gradleRepositoryPath, IDENTIFIER);
  }

  static @Nullable String initializeGradleRepositoryPath() {
    String gradleUserHomePath = System.getenv(GRADLE_USER_HOME);
    String gradleUserHome = gradleUserHomePath == null ? SystemProperties.getUserHome() + File.separator + ".gradle" : gradleUserHomePath;
    return Files.exists(Path.of(gradleUserHome)) ? PathRelativizerService.normalizePath(gradleUserHome) : null;
  }
}
