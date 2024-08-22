// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.builders.storage;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.builders.BuildTarget;
import org.jetbrains.jps.builders.BuildTargetType;

import java.io.File;
import java.nio.file.Path;

public interface BuildDataPaths {
  @NotNull File getDataStorageRoot();

  @NotNull File getTargetsDataRoot();

  @NotNull File getTargetTypeDataRoot(@NotNull BuildTargetType<?> targetType);

  @NotNull File getTargetDataRoot(@NotNull BuildTarget<?> target);

  @NotNull Path getTargetDataRootDir(@NotNull BuildTarget<?> target);

  @NotNull
  Path getTargetDataRoot(@NotNull BuildTargetType<?> targetType, @NotNull String targetId);
}
