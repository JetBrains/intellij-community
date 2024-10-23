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
  public @Nullable GenericZipEntry getEntry(String entryName) throws IOException {
    JBZipEntry entry = myZipFile.getEntry(entryName);
    if (entry == null) return null;
    return new JBZipFileEntryWrapper(entry);
  }

  @Override
  public @NotNull List<? extends GenericZipEntry> getEntries() {
    List<JBZipEntry> entries = myZipFile.getEntries();
    ArrayList<JBZipFileEntryWrapper> list = new ArrayList<>(entries.size());
    for (JBZipEntry entry : entries) {
      list.add(new JBZipFileEntryWrapper(entry));
    }
    return list;
  }

  @Override
  public void close() throws IOException {
    myZipFile.close();
  }
}

class JBZipFileEntryWrapper implements GenericZipEntry {
  private final JBZipEntry entry;

  JBZipFileEntryWrapper(JBZipEntry entry) { this.entry = entry; }


  @Override
  public long getSize() {
    return entry.getSize();
  }

  @Override
  public String getName() {
    return entry.getName();
  }

  @Override
  public long getCrc() {
    return entry.getCrc();
  }

  @Override
  public boolean isDirectory() {
    return entry.isDirectory();
  }

  @Override
  public InputStream getInputStream() throws IOException {
    return entry.getInputStream();
  }
}