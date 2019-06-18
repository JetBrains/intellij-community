// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.incremental.relativizer;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class ProjectPathRelativizer implements PathRelativizer {
  private static final String IDENTIFIER = "$PROJECT_DIR$";
  @Nullable private final String myProjectPath;

  ProjectPathRelativizer(@Nullable String projectPath) {
    myProjectPath = projectPath;
  }

  @Override
  public boolean isAcceptableAbsolutePath(@NotNull String path) {
    return myProjectPath != null && path.contains(myProjectPath);
  }

  @Override
  public boolean isAcceptableRelativePath(@NotNull String path) {
    return myProjectPath != null && path.contains(IDENTIFIER);
  }

  @Override
  public String toRelativePath(@NotNull String path) {
    if (myProjectPath == null) return path;
    int i = path.indexOf(myProjectPath);
    if (i < 0) return path;
    return IDENTIFIER + path.substring(i + myProjectPath.length());
  }

  @Override
  public String toAbsolutePath(@NotNull String path) {
    if (myProjectPath == null) return path;
    int i = path.indexOf(IDENTIFIER);
    if (i < 0) return path;
    return myProjectPath + path.substring(i + IDENTIFIER.length());
  }
}
