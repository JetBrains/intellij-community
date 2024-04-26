// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.incremental.relativizer;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.SystemProperties;
import org.jetbrains.annotations.Nullable;

import java.io.File;

final class GradlePathRelativizer extends CommonPathRelativizer {
  private static final String IDENTIFIER = "$GRADLE_REPOSITORY$";
  private static final String GRADLE_USER_HOME = "GRADLE_USER_HOME";

  GradlePathRelativizer() {
    super(initializeGradleRepositoryPath(), IDENTIFIER);
  }

  private static @Nullable String initializeGradleRepositoryPath() {
    String gradleUserHomePath = System.getenv(GRADLE_USER_HOME);
    String gradleUserHome = gradleUserHomePath == null ? SystemProperties.getUserHome() + File.separator + ".gradle" : gradleUserHomePath;

    if (FileUtil.exists(gradleUserHome)) {
      return PathRelativizerService.normalizePath(gradleUserHome);
    }
    return null;
  }
}
