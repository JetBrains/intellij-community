// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.impl;

import com.intellij.util.io.FileAccessorCache;
import com.intellij.util.io.ResourceHandle;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.attribute.BasicFileAttributes;

public class ZipHandler extends ZipHandlerBase {
  private static final FileAccessorCache<ZipHandler, GenericZipFile> ourZipFileFileAccessorCache = new FileAccessorCache<ZipHandler, GenericZipFile>(20, 10) {
    @Override
    protected @NotNull GenericZipFile createAccessor(ZipHandler handler) throws IOException {
      File file = handler.getFile();
      BasicFileAttributes attrs = Files.readAttributes(file.toPath(), BasicFileAttributes.class);
      handler.myFileStamp = attrs.lastModifiedTime().toMillis();
      handler.myFileLength = attrs.size();
      return getZipFileWrapper(file);
    }

    @Override
    protected void disposeAccessor(@NotNull GenericZipFile fileAccessor) throws IOException {
      fileAccessor.close();
    }

    @Override
    public boolean isEqual(ZipHandler val1, ZipHandler val2) {
      return val1 == val2;  // reference equality to handle different jars for different ZipHandlers on the same path
    }
  };

  private volatile long myFileStamp;
  private volatile long myFileLength;

  public ZipHandler(@NotNull String path) {
    super(path);
  }

  @Override
  protected @NotNull ResourceHandle<GenericZipFile> acquireZipHandle() throws IOException {
    try {
      FileAccessorCache.Handle<GenericZipFile> handle = ourZipFileFileAccessorCache.get(this);

      // IDEA-148458, JDK-4425695 (JVM crashes on accessing an open ZipFile after it was modified)
      File file = getFile();
      BasicFileAttributes attrs = Files.readAttributes(file.toPath(), BasicFileAttributes.class);
      if (attrs.lastModifiedTime().toMillis() != myFileStamp || attrs.size() != myFileLength) {
        // Note that zip_util.c#ZIP_Get_From_Cache will allow us to have duplicated ZipFile instances without a problem
        clearCaches();
        handle.release();
        handle = ourZipFileFileAccessorCache.get(this);
      }

      return handle;
    }
    catch (RuntimeException e) {
      Throwable cause = e.getCause();
      if (cause instanceof IOException) throw (IOException)cause;
      throw e;
    }
  }

  @Override
  public void clearCaches() {
    ourZipFileFileAccessorCache.remove(this);
    super.clearCaches();
  }

  @Override
  protected long getEntryFileStamp() {
    return myFileStamp;
  }

  @ApiStatus.Internal
  public static void clearFileAccessorCache() {
    ourZipFileFileAccessorCache.clear();
  }
}
