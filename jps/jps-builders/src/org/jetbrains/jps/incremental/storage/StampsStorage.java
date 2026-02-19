// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.incremental.storage;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.builders.BuildTarget;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;

public interface StampsStorage<T> {
  void updateStamp(@NotNull Path file, BuildTarget<?> buildTarget, long currentFileTimestamp) throws IOException;

  void removeStamp(@NotNull Path file, BuildTarget<?> buildTarget) throws IOException;

  @Nullable
  T getCurrentStampIfUpToDate(@NotNull Path file, BuildTarget<?> target, @Nullable BasicFileAttributes attrs) throws IOException;
}
