// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.builders.storage;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.builders.BuildTarget;
import org.jetbrains.jps.builders.BuildTargetType;

import java.io.File;
import java.nio.file.Path;

public interface BuildDataPaths {
  @NotNull Path getDataStorageDir();

  /**
   * @deprecated Use {@link #getDataStorageDir}.
   */
  @SuppressWarnings("IO_FILE_USAGE")
  @Deprecated
  default @NotNull File getDataStorageRoot() {
    return getDataStorageDir().toFile();
  }

  @NotNull Path getTargetsDataRoot();

  /**
   * @deprecated Use {@link #getTargetTypeDataRootDir}.
   */
  @SuppressWarnings("IO_FILE_USAGE")
  @Deprecated
  default @NotNull File getTargetTypeDataRoot(@NotNull BuildTargetType<?> targetType) {
    return getTargetTypeDataRootDir(targetType).toFile();
  }

  @NotNull Path getTargetTypeDataRootDir(@NotNull BuildTargetType<?> targetType);

  /**
   * @deprecated Use {@link #getTargetDataRootDir}.
   */
  @SuppressWarnings("IO_FILE_USAGE")
  @Deprecated
  default @NotNull File getTargetDataRoot(@NotNull BuildTarget<?> target) {
    return getTargetDataRootDir(target).toFile();
  }

  @NotNull Path getTargetDataRootDir(@NotNull BuildTarget<?> target);

  @NotNull
  Path getTargetDataRoot(@NotNull BuildTargetType<?> targetType, @NotNull String targetId);
}
