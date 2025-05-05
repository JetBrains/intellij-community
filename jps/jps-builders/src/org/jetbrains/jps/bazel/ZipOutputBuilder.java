// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.bazel;

import org.jetbrains.annotations.Nullable;

import java.io.Closeable;
import java.io.IOException;

public interface ZipOutputBuilder extends Closeable {

  Iterable<String> getEntryNames();

  Iterable<String> listEntries(String entryName);

  byte @Nullable [] getContent(String entryName);

  void putEntry(String entryName, byte[] content);

  boolean deleteEntry(String entryName);

  @Override
  default void close() throws IOException {
    close(true);
  }

  void close(boolean saveChanges) throws IOException;


  static @Nullable String getParentEntryName(String entryName) {
    if (entryName == null || entryName.isEmpty() || "/".equals(entryName)) {
      return null;
    }
    int idx = isDirectoryName(entryName)? entryName.lastIndexOf('/', entryName.length() - 2) : entryName.lastIndexOf('/');
    return idx > 0? entryName.substring(0, idx + 1) : "/";
  }

  @Nullable
  static String getShortName(String entryName) {
    if (entryName == null || entryName.isEmpty() || "/".equals(entryName)) {
      return entryName;
    }
    int idx = isDirectoryName(entryName)? entryName.lastIndexOf('/', entryName.length() - 2) : entryName.lastIndexOf('/');
    return idx >= 0? entryName.substring(idx + 1) : entryName;
  }

  static boolean isDirectoryName(String entryName) {
    return entryName.endsWith("/");
  }
}
