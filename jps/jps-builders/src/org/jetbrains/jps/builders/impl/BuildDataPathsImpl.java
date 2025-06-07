// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.builders.impl;

import com.intellij.util.PathUtilRt;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.builders.BuildTarget;
import org.jetbrains.jps.builders.BuildTargetType;
import org.jetbrains.jps.builders.storage.BuildDataPaths;

import java.io.File;
import java.nio.file.Path;

@ApiStatus.Internal
public final class BuildDataPathsImpl implements BuildDataPaths {
  private final Path dir;

  /**
   * @deprecated Use {@link #BuildDataPathsImpl(Path)}
   */
  @SuppressWarnings("IO_FILE_USAGE")
  @Deprecated
  public BuildDataPathsImpl(@NotNull File dataStorageRoot) {
    dir = dataStorageRoot.toPath();
  }

  public BuildDataPathsImpl(@NotNull Path dataStorageRoot) {
    dir = dataStorageRoot;
  }

  @Override
  public @NotNull Path getDataStorageDir() {
    return dir;
  }

  @Override
  public @NotNull Path getTargetsDataRoot() {
    return dir.resolve("targets");
  }

  @Override
  public @NotNull Path getTargetTypeDataRootDir(@NotNull BuildTargetType<?> targetType) {
    return dir.resolve("targets").resolve(targetType.getTypeId());
  }

  @Override
  public @NotNull Path getTargetDataRootDir(@NotNull BuildTarget<?> target) {
    return getTargetDataRoot(target.getTargetType(), target.getId());
  }

  @Override
  public @NotNull Path getTargetDataRoot(@NotNull BuildTargetType<?> targetType, @NotNull String targetId) {
    return getTargetTypeDataRootDir(targetType).resolve(targetIdToFilename(targetId));
  }

  private static @NotNull String targetIdToFilename(@NotNull String targetId) {
    // targetId may diff from another targetId only in case
    // when used as a file name in case-insensitive file systems, both paths for different targets will point to the same dir
    return PathUtilRt.suggestFileName(targetId, true, false) + "_" + Integer.toHexString(targetId.hashCode());
  }
}
