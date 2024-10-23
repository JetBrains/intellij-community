// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.impl;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

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
  public GenericZipEntry getEntry(String entryName) throws IOException {
    ZipEntry entry = myZipFile.getEntry(entryName);
    if (entry == null) return null;
    return new JavaZipFileEntryWrapper(entry, myZipFile);
  }

  @Override
  public @NotNull List<? extends GenericZipEntry> getEntries() {
    Enumeration<? extends ZipEntry> entries = myZipFile.entries();
    ArrayList<JavaZipFileEntryWrapper> list = new ArrayList<>();
    while (entries.hasMoreElements()) {
      ZipEntry entry = entries.nextElement();
      list.add(new JavaZipFileEntryWrapper(entry, myZipFile));
    }
    return list;
  }

  @Override
  public void close() throws IOException {
    myZipFile.close();
  }
}

class JavaZipFileEntryWrapper implements GenericZipEntry {
  private final ZipEntry entry;
  private final ZipFile myFile;

  JavaZipFileEntryWrapper(ZipEntry entry, ZipFile file) {
    this.entry = entry;
    myFile = file;
  }

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
    return myFile.getInputStream(entry);
  }
}