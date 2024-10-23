// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.OSAgnosticPathUtil;
import com.intellij.util.io.FileAccessorCache;
import com.intellij.util.io.ResourceHandle;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;

public class ZipHandler extends ZipHandlerBase {
  private static final Logger LOG = Logger.getInstance(ZipHandler.class);

  private static final FileAccessorCache<ZipHandler, GenericZipFile> ourZipFileFileAccessorCache =
    new FileAccessorCache<ZipHandler, GenericZipFile>(20, 10) {
      @Override
      protected @NotNull GenericZipFile createAccessor(ZipHandler handler) throws IOException {
        File file = handler.getFile();
        Path path = file.toPath();
        BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class);
        handler.myFileStamp = attrs.lastModifiedTime().toMillis();
        handler.myFileLength = attrs.size();
        if (isFileLikelyLocal(file)) {
          LOG.trace("Using java.util.zip.ZipFile to open " + file.getPath());
          return new JavaZipFileWrapper(file);
        }
        else {
          LOG.trace("Using JBZipFile to open " + file.getPath());
          return new JBZipFileWrapper(file);
        }
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

  /**
   * This is a heuristic that helps to decide whether file should be handled by ZipFile or JBZipFile.
   */
  @ApiStatus.Internal
  public static boolean isFileLikelyLocal(@NotNull File file) {
    if (SystemInfo.isWindows) {
      // WSL is locally reachable from Windows, hence we want to detect if the file resides completely at Windows side
      return OSAgnosticPathUtil.startsWithWindowsDrive(file.getPath());
    }
    else {
      // We intentionally use java.io here, as we need to ask the local file system if it recognizes the path.
      return file.exists();
    }
  }

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

  // also used in Kotlin
  public static void clearFileAccessorCache() {
    ourZipFileFileAccessorCache.clear();
  }
}
