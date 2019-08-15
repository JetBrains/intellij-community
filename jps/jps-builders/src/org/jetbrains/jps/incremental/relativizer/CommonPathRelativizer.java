// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.incremental.relativizer;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

abstract class CommonPathRelativizer implements PathRelativizer{
  protected final String myPath;
  private final String myIdentifier;

  CommonPathRelativizer(@Nullable String path, @NotNull String identifier) {
    myPath = path;
    myIdentifier = identifier;
  }

  @Override
  public boolean isAcceptableAbsolutePath(@NotNull String path) {
    return myPath != null && path.startsWith(myPath);
  }

  @Override
  public boolean isAcceptableRelativePath(@NotNull String path) {
    return myPath != null && path.startsWith(myIdentifier);
  }

  @Override
  public String toRelativePath(@NotNull String path) {
    if (myPath == null) return path;
    int i = path.indexOf(myPath);
    if (i < 0) return path;
    return myIdentifier + path.substring(i + myPath.length());
  }

  @Override
  public String toAbsolutePath(@NotNull String path) {
    if (myPath == null) return path;
    int i = path.indexOf(myIdentifier);
    if (i < 0) return path;
    return myPath + path.substring(i + myIdentifier.length());
  }
}
