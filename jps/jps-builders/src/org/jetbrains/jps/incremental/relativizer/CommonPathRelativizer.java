// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.incremental.relativizer;

import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class CommonPathRelativizer implements PathRelativizer {
  private final String myPath;
  private final String myIdentifier;
  /**
   * can be null when myPath is null, or it is impossible to detect the sensitivity of the file system
   */
  @Nullable
  private final Boolean myIsCaseSensitive;

  CommonPathRelativizer(@Nullable String path, @NotNull String identifier) {
    this(path, identifier, null);
  }

  CommonPathRelativizer(@Nullable String path, @NotNull String identifier, @Nullable Boolean isCaseSensitive) {
    myPath = path;
    myIdentifier = identifier;
    myIsCaseSensitive = isCaseSensitive;
  }

  @Override
  public @Nullable String toRelativePath(@NotNull String path) {
    if (myPath == null ||
        !(myIsCaseSensitive != null ? FileUtil.startsWith(path, myPath, myIsCaseSensitive) : FileUtil.startsWith(path, myPath))) {
      return null;
    }
    return myIdentifier + path.substring(myPath.length());
  }

  @Override
  public @Nullable String toAbsolutePath(@NotNull String path) {
    if (myPath == null || !path.startsWith(myIdentifier)) return null;
    return myPath + path.substring(myIdentifier.length());
  }
}
