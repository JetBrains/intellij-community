// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
  private final File myDataStorageRoot;

  public BuildDataPathsImpl(@NotNull File dataStorageRoot) {
    myDataStorageRoot = dataStorageRoot;
  }

  @Override
  public @NotNull File getDataStorageRoot() {
    return myDataStorageRoot;
  }

  @Override
  public @NotNull File getTargetsDataRoot() {
    return new File(myDataStorageRoot, "targets");
  }

  @Override
  public @NotNull File getTargetTypeDataRoot(@NotNull BuildTargetType<?> targetType) {
    return new File(getTargetsDataRoot(), targetType.getTypeId());
  }

  public @NotNull Path getTargetTypeDataRootDir(@NotNull BuildTargetType<?> targetType) {
    return getTargetsDataRoot().toPath().resolve(targetType.getTypeId());
  }

  @Override
  public @NotNull File getTargetDataRoot(@NotNull BuildTarget<?> target) {
    return getTargetDataRoot(target.getTargetType(), target.getId()).toFile();
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
