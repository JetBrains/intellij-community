// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.impl;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

@ApiStatus.Internal
public final class JavaZipFileWrapper implements GenericZipFile {
  private final ZipFile myZipFile;

  public JavaZipFileWrapper(File file) throws IOException {
    myZipFile = new ZipFile(file);
  }

  @Override
  public GenericZipEntry getEntry(@NotNull String entryName) throws IOException {
    ZipEntry entry = myZipFile.getEntry(entryName);
    return entry != null ? new EntryWrapper(entry, myZipFile) : null;
  }

  @Override
  public @NotNull List<? extends GenericZipEntry> getEntries() {
    Enumeration<? extends ZipEntry> entries = myZipFile.entries();
    List<EntryWrapper> list = new ArrayList<>();
    while (entries.hasMoreElements()) {
      ZipEntry entry = entries.nextElement();
      list.add(new EntryWrapper(entry, myZipFile));
    }
    return list;
  }

  @Override
  public void close() throws IOException {
    myZipFile.close();
  }

  private static class EntryWrapper implements GenericZipFile.GenericZipEntry {
    private final ZipEntry myEntry;
    private final ZipFile myFile;

    EntryWrapper(ZipEntry entry, ZipFile file) {
      myEntry = entry;
      myFile = file;
    }

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
      return myFile.getInputStream(myEntry);
    }
  }
}
