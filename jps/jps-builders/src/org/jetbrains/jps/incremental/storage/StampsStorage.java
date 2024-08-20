// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.incremental.storage;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.builders.BuildTarget;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;

import static org.jetbrains.jps.incremental.storage.StampsStorage.Stamp;

/**
 * @author Eugene Zhuravlev
 */
public interface StampsStorage<T extends Stamp> {
  File getStorageRoot();

  void force();

  void saveStamp(File file, BuildTarget<?> buildTarget, T stamp) throws IOException;

  void removeStamp(File file, BuildTarget<?> buildTarget) throws IOException;

  void clean() throws IOException;

  T getPreviousStamp(File file, BuildTarget<?> target) throws IOException;

  T getCurrentStamp(Path file) throws IOException;

  boolean wipe();

  void close() throws IOException;

  boolean isDirtyStamp(Stamp stamp, File file) throws IOException;

  boolean isDirtyStamp(Stamp stamp, File file, @NotNull BasicFileAttributes attrs) throws IOException;

  interface Stamp { }
}
