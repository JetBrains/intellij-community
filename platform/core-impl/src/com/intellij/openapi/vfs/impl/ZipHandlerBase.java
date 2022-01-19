// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.BufferExposingByteArrayInputStream;
import com.intellij.openapi.util.io.FileTooBigException;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.util.io.ResourceHandle;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.VisibleForTesting;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public abstract class ZipHandlerBase extends ArchiveHandler {
  private static final Logger LOG = Logger.getInstance(ZipHandlerBase.class);

  public @NotNull Map<String, Long> getArchiveCrcHashes() throws IOException {
    try (@NotNull ResourceHandle<ZipFile> handle = acquireZipHandle()) {
      ZipFile file = handle.get();
      Enumeration<? extends ZipEntry> entries = file.entries();
      Map<String, Long> result = new Object2LongOpenHashMap<>();
      while (entries.hasMoreElements()) {
        ZipEntry entry = entries.nextElement();
        result.put(normalizeName(entry.getName()), entry.getCrc());
      }
      return result;
    }
  }

  @ApiStatus.Internal
  public static boolean getUseCrcInsteadOfTimestampPropertyValue() {
    return Boolean.getBoolean("zip.handler.uses.crc.instead.of.timestamp");
  }

  public ZipHandlerBase(@NotNull String path) {
    super(path);
  }

  @Override
  protected @NotNull Map<String, EntryInfo> createEntriesMap() throws IOException {
    try (ResourceHandle<ZipFile> zipRef = acquireZipHandle()) {
      return buildEntryMapForZipFile(zipRef.get());
    }
  }

  protected @NotNull Map<String, EntryInfo> buildEntryMapForZipFile(@NotNull ZipFile zip) {
    Map<String, EntryInfo> map = new ZipEntryMap(zip.size());

    Enumeration<? extends ZipEntry> entries = zip.entries();
    while (entries.hasMoreElements()) {
      ZipEntry ze = entries.nextElement();
      processEntry(map, LOG, ze.getName(), ze.isDirectory() ? null : (parent, name) -> {
        long fileStamp = getUseCrcInsteadOfTimestampPropertyValue() ? ze.getCrc() : getEntryFileStamp();
        return new EntryInfo(name, false, ze.getSize(), fileStamp, parent);
      });
    }

    return map;
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

  @Override
  public @NotNull InputStream getInputStream(@NotNull String relativePath) throws IOException {
    boolean release = true;
    ResourceHandle<ZipFile> zipRef = acquireZipHandle();
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

  protected abstract @NotNull ResourceHandle<ZipFile> acquireZipHandle() throws IOException;

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
