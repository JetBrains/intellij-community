// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.incremental.storage;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.builders.BuildTarget;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;

import static org.jetbrains.jps.incremental.storage.StampsStorage.Stamp;

/**
 * @author Eugene Zhuravlev
 */
public interface StampsStorage<T extends Stamp> {
  Path getStorageRoot();

  void force();

  void saveStamp(@NotNull Path file, BuildTarget<?> buildTarget, @NotNull T stamp) throws IOException;

  void removeStamp(@NotNull Path file, BuildTarget<?> buildTarget) throws IOException;

  void clean() throws IOException;

  @Nullable
  T getPreviousStamp(@NotNull Path file, BuildTarget<?> target) throws IOException;

  @NotNull
  T getCurrentStamp(@NotNull Path file) throws IOException;

  boolean wipe();

  void close() throws IOException;

  boolean isDirtyStamp(@NotNull Stamp stamp, @NotNull Path file) throws IOException;

  boolean isDirtyStamp(@Nullable Stamp stamp, @NotNull Path file, @NotNull BasicFileAttributes attrs) throws IOException;

  interface Stamp { }
}
