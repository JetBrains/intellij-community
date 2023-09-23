// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.builders.impl;

import com.intellij.util.PathUtilRt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.builders.BuildTarget;
import org.jetbrains.jps.builders.BuildTargetType;
import org.jetbrains.jps.builders.storage.BuildDataPaths;

import java.io.File;

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

  @Override
  public @NotNull File getTargetDataRoot(@NotNull BuildTarget<?> target) {
    BuildTargetType<?> targetType = target.getTargetType();
    final String targetId = target.getId();
    return getTargetDataRoot(targetType, targetId);
  }

  @Override
  public @NotNull File getTargetDataRoot(@NotNull BuildTargetType<?> targetType, @NotNull String targetId) {
    // targetId may diff from another targetId only in case
    // when used as a file name in case-insensitive file systems, both paths for different targets will point to the same dir
    return new File(getTargetTypeDataRoot(targetType), PathUtilRt.suggestFileName(targetId + "_" + Integer.toHexString(targetId.hashCode()), true, false));
  }
}
