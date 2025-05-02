// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.bazel;

import org.jetbrains.annotations.Nullable;

import java.io.Closeable;
import java.io.IOException;

public interface ZipOutputBuilder extends Closeable {

  Iterable<String> getEntryNames();

  boolean isDirectory(String entryName);

  Iterable<String> listEntries(String entryName);

  byte @Nullable [] getContent(String entryName);

  void putEntry(String entryName, byte[] content);

  boolean deleteEntry(String entryName);

  @Override
  default void close() throws IOException {
    close(true);
  }

  void close(boolean saveChanges) throws IOException;
}
