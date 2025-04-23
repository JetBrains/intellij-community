// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.bazel.impl;

import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.bazel.ZipOutputBuilder;

import java.io.DataOutput;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class ZipOutputBuilderImpl implements ZipOutputBuilder {
  private static final byte[] EMPTY_BYTES = new byte[0];
  
  private final Map<String, EntryData> myEntries = new TreeMap<>();

  public ZipOutputBuilderImpl(Path outputZip) {
    // todo: init from the previous zip
  }

  @Override
  public Iterable<String> getEntryNames() {
    return myEntries.keySet();
  }

  @Override
  public boolean isDirectory(String entryName) {
    return isDirectoryName(entryName);
  }

  @Override
  public byte[] getContent(String entryName) {
    try {
      return myEntries.getOrDefault(entryName, EntryData.EMPTY).getContent();
    }
    catch (IOException e) {
      // todo: diagnostics
      return EMPTY_BYTES;
    }
  }

  @Override
  public void putEntry(String entryName, byte[] content) {
    // todo: create intermediate directory entries
    myEntries.put(entryName, EntryData.create(content));
  }

  @Override
  public void deleteEntry(String entryName) {
    if (myEntries.remove(entryName) != null) {
      // todo: update parent intermediate entry
    }
  }

  @Override
  public void write(DataOutput out) {
    // todo
  }

  @Nullable
  private static String getParent(String entryName) {
    int idx = isDirectoryName(entryName)? entryName.lastIndexOf('/', entryName.length() - 2) : entryName.lastIndexOf('/');
    return idx >= 0? entryName.substring(0, idx + 1) : null;
  }

  private static boolean isDirectoryName(String entryName) {
    return entryName.endsWith("/");
  }

  private interface EntryData {
    EntryData EMPTY = () -> EMPTY_BYTES;

    byte[] getContent() throws IOException;

    static EntryData create(byte[] content) {
      return () -> content;
    }

    static EntryData create(ZipFile zf, ZipEntry ze) {
      return new EntryData() {
        private byte[] loaded;
        @Override
        public byte[] getContent() throws IOException {
          if (loaded == null) {
            try (InputStream is = zf.getInputStream(ze)) {
              loaded = is.readAllBytes();
            }
          }
          return loaded;
        }
      };
    }
  }

}
