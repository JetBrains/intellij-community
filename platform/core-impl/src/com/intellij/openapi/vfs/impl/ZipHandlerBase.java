// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Trinity;
import com.intellij.openapi.util.io.BufferExposingByteArrayInputStream;
import com.intellij.openapi.util.io.FileTooBigException;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.SystemProperties;
import com.intellij.util.io.ResourceHandle;
import com.intellij.util.text.ByteArrayCharSequence;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public abstract class ZipHandlerBase extends ArchiveHandler {
  @ApiStatus.Internal
  public static final boolean USE_CRC_INSTEAD_OF_TIMESTAMP = SystemProperties.is("zip.handler.uses.crc.instead.of.timestamp");

  public ZipHandlerBase(@NotNull String path) {
    super(path);
  }

  @NotNull
  @Override
  protected Map<String, EntryInfo> createEntriesMap() throws IOException {
    try (ResourceHandle<ZipFile> existingZipRef = acquireZipHandle()) {
      return buildEntryMapForZipFile(existingZipRef.get());
    }
  }

  @NotNull
  protected Map<String, EntryInfo> buildEntryMapForZipFile(@NotNull ZipFile zip) {
    Map<String, EntryInfo> map = new ZipEntryMap(zip.size());
    map.put("", createRootEntry());

    Enumeration<? extends ZipEntry> entries = zip.entries();
    while (entries.hasMoreElements()) {
      getOrCreate(entries.nextElement(), map, zip);
    }

    return map;
  }

  @NotNull
  private EntryInfo getOrCreate(@NotNull ZipEntry entry, @NotNull Map<String, EntryInfo> map, @NotNull ZipFile zip) {
    boolean isDirectory = entry.isDirectory();
    String entryName = entry.getName();
    if (StringUtil.endsWithChar(entryName, '/')) {
      entryName = entryName.substring(0, entryName.length() - 1);
      isDirectory = true;
    }
    if (StringUtil.startsWithChar(entryName, '/') || StringUtil.startsWithChar(entryName, '\\')) {
      entryName = entryName.substring(1);
    }

    EntryInfo info = map.get(entryName);
    if (info != null) return info;

    Trinity<String, String, String> path = splitPathAndFix(entryName);
    EntryInfo parentInfo = getOrCreate(path.first, map, zip);
    if (".".equals(path.second)) {
      return parentInfo;
    }
    long fileStamp = USE_CRC_INSTEAD_OF_TIMESTAMP ? entry.getCrc() : getEntryFileStamp();
    info = store(map, parentInfo, path.second, isDirectory, entry.getSize(), fileStamp, path.third);
    return info;
  }

  @NotNull
  private static EntryInfo store(@NotNull Map<String, EntryInfo> map,
                                 @Nullable EntryInfo parentInfo,
                                 @NotNull CharSequence shortName,
                                 boolean isDirectory,
                                 long size,
                                 long time,
                                 @NotNull String entryName) {
    CharSequence sequence = ByteArrayCharSequence.convertToBytesIfPossible(shortName);
    EntryInfo info = new EntryInfo(sequence, isDirectory, size, time, parentInfo);
    map.put(entryName, info);
    return info;
  }

  @NotNull
  private EntryInfo getOrCreate(@NotNull String entryName, @NotNull Map<String, EntryInfo> map, @NotNull ZipFile zip) {
    EntryInfo info = map.get(entryName);

    if (info == null) {
      ZipEntry entry = zip.getEntry(entryName + "/");
      if (entry != null) {
        return getOrCreate(entry, map, zip);
      }

      Trinity<String, String, String> path = splitPathAndFix(entryName);
      if (entryName.equals(path.first)) {
        throw new IllegalArgumentException("invalid entry name: '"+entryName+"' in "+zip.getName()+"; after split: "+path);
      }
      EntryInfo parentInfo = getOrCreate(path.first, map, zip);
      entryName = path.third;
      info = store(map, parentInfo, path.second, true, DEFAULT_LENGTH, DEFAULT_TIMESTAMP, entryName);
    }

    if (!info.isDirectory) {
      Logger.getInstance(getClass()).info(zip.getName() + ": " + entryName + " should be a directory");
      info = store(map, info.parent, info.shortName, true, info.length, info.timestamp, entryName);
    }

    return info;
  }

  public long getEntryCrc(@NotNull String relativePath) throws IOException {
    try (ResourceHandle<ZipFile> zipRef = acquireZipHandle()) {
      ZipFile zip = zipRef.get();
      ZipEntry entry = zip.getEntry(relativePath);
      if (entry != null) {
        return entry.getCrc();
      }
    }

    throw new FileNotFoundException(getFile() + "!/" + relativePath);
  }

  @Override
  public byte @NotNull [] contentsToByteArray(@NotNull String relativePath) throws IOException {
    try (ResourceHandle<ZipFile> zipRef = acquireZipHandle()) {
      ZipFile zip = zipRef.get();
      ZipEntry entry = zip.getEntry(relativePath);
      if (entry != null) {
        long length = entry.getSize();
        if (FileUtilRt.isTooLarge(length)) {
          throw new FileTooBigException(getFile() + "!/" + relativePath);
        }
        try (InputStream stream = zip.getInputStream(entry)) {
          if (stream != null) {
            // ZipFile.c#Java_java_util_zip_ZipFile_read reads data in 8K (stack allocated) blocks - no sense to create BufferedInputStream
            return FileUtil.loadBytes(stream, (int)length);
          }
        }
      }
    }

    throw new FileNotFoundException(getFile() + "!/" + relativePath);
  }

  @NotNull
  @Override
  public InputStream getInputStream(@NotNull String relativePath) throws IOException {
    boolean release = true;
    final ResourceHandle<ZipFile> zipRef = acquireZipHandle();
    try {
      ZipFile zip = zipRef.get();
      ZipEntry entry = zip.getEntry(relativePath);
      if (entry != null) {
        InputStream stream = zip.getInputStream(entry);
        if (stream != null) {
          long length = entry.getSize();
          if (!FileUtilRt.isTooLarge(length)) {
            try {
              return new BufferExposingByteArrayInputStream(FileUtil.loadBytes(stream, (int)length));
            }
            finally {
              stream.close();
            }
          }
          else {
            release = false;
            return new InputStreamWrapper(stream, zipRef);
          }
        }
      }
    }
    finally {
      if (release) zipRef.close();
    }

    throw new FileNotFoundException(getFile() + "!/" + relativePath);
  }

  protected abstract long getEntryFileStamp();

  @NotNull
  protected abstract ResourceHandle<ZipFile> acquireZipHandle() throws IOException;

  private static class InputStreamWrapper extends InputStream {
    private final InputStream myStream;
    private final ResourceHandle<ZipFile> myZipRef;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    InputStreamWrapper(InputStream stream, ResourceHandle<ZipFile> zipRef) {
      myStream = stream;
      myZipRef = zipRef;
    }

    @Override
    public int read() throws IOException {
      return myStream.read();
    }

    @Override
    public int read(byte @NotNull [] b, int off, int len) throws IOException {
      return myStream.read(b, off, len);
    }

    @Override
    public int available() throws IOException {
      return myStream.available();
    }

    @Override
    public void close() throws IOException {
      if (!closed.getAndSet(true)) {
        try {
          myStream.close();
        }
        finally {
          myZipRef.close();
        }
      }
    }
  }
}