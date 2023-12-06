// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.incremental.relativizer;

import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Unlike {@link SubPathRelativizer} which masks only subdirectories or subfiles, this one {@link AnyPathRelativizer} can handle relative
 * paths containing '/../' components as well.
 */
final class AnyPathRelativizer implements PathRelativizer {
  private final Path myPath;
  private final String myIdentifier;

  AnyPathRelativizer(@Nullable String path, @NotNull String identifier) {
    myPath = path != null ? Paths.get(path) : null;
    myIdentifier = identifier;
  }

  @Override
  public @Nullable String toRelativePath(@NotNull String path) {
    if (myPath == null) return null;

    Path rel;
    try {
      rel = myPath.relativize(Paths.get(path));
    }
    catch (IllegalArgumentException ignored) {
      return null;  // {@code path} cannot be relativized, ignore
    }

    return FileUtil.toSystemIndependentName(myIdentifier + '/' + rel);
  }

  @Override
  public @Nullable String toAbsolutePath(@NotNull String path) {
    if (myPath == null || !path.startsWith(myIdentifier)) return null;
    var abs = Paths.get(myPath + path.substring(myIdentifier.length())).normalize();
    return FileUtil.toSystemIndependentName(abs.toString());
  }
}
