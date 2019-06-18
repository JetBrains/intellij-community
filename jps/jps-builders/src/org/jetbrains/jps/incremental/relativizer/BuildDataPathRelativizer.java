// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.incremental.relativizer;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class BuildDataPathRelativizer implements PathRelativizer{
  private static final String IDENTIFIER = "$BUILD_DIR$";
  @Nullable private final String myBuildDir;

  BuildDataPathRelativizer(@Nullable String buildDir) {
    myBuildDir = buildDir;
  }

  @Override
  public boolean isAcceptableAbsolutePath(@NotNull String path) {
    return myBuildDir != null && path.contains(myBuildDir);
  }

  @Override
  public boolean isAcceptableRelativePath(@NotNull String path) {
    return myBuildDir != null && path.contains(IDENTIFIER);
  }

  @Override
  public String toRelativePath(@NotNull String path) {
    if (myBuildDir == null) return path;
    int i = path.indexOf(myBuildDir);
    if (i < 0) return path;
    return IDENTIFIER + path.substring(i + myBuildDir.length());
  }

  @Override
  public String toAbsolutePath(@NotNull String path) {
    if (myBuildDir == null) return path;
    int i = path.indexOf(IDENTIFIER);
    if (i < 0) return path;
    return myBuildDir + path.substring(i + IDENTIFIER.length());
  }
}
