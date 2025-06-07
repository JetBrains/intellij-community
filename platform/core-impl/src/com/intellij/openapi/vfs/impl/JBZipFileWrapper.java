// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.impl;

import com.intellij.util.io.zip.JBZipEntry;
import com.intellij.util.io.zip.JBZipFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

@ApiStatus.Internal
public final class JBZipFileWrapper implements GenericZipFile {
  private final JBZipFile myZipFile;

  public JBZipFileWrapper(File file) throws IOException {
    myZipFile = new JBZipFile(file, true);
  }

  @Override
  public @Nullable GenericZipEntry getEntry(@NotNull String entryName) throws IOException {
    JBZipEntry entry = myZipFile.getEntry(entryName);
    return entry != null ? new EntryWrapper(entry) : null;
  }

  @Override
  public @NotNull List<? extends GenericZipEntry> getEntries() {
    List<JBZipEntry> entries = myZipFile.getEntries();
    List<EntryWrapper> list = new ArrayList<>(entries.size());
    for (JBZipEntry entry : entries) {
      list.add(new EntryWrapper(entry));
    }
    return list;
  }

  @Override
  public void close() throws IOException {
    myZipFile.close();
  }

  private static class EntryWrapper implements GenericZipEntry {
    private final JBZipEntry myEntry;

    EntryWrapper(JBZipEntry entry) { myEntry = entry; }

    @Override
    public long getSize() {
      return myEntry.getSize();
    }

    @Override
    public @NotNull String getName() {
      return myEntry.getName();
    }

    @Override
    public long getCrc() {
      return myEntry.getCrc();
    }

    @Override
    public boolean isDirectory() {
      return myEntry.isDirectory();
    }

    @Override
    public @Nullable InputStream getInputStream() throws IOException {
      return myEntry.getInputStream();
    }
  }
}
