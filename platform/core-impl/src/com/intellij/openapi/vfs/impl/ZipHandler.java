/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.vfs.impl;

import com.intellij.openapi.util.io.FileAttributes;
import com.intellij.openapi.util.io.FileSystemUtil;
import com.intellij.util.io.FileAccessorCache;
import com.intellij.util.io.ResourceHandle;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.zip.ZipFile;

public class ZipHandler extends ZipHandlerBase {
  private volatile String myCanonicalPathToZip;
  private volatile long myFileStamp;
  private volatile long myFileLength;

  public ZipHandler(@NotNull String path) {
    super(path);
  }

  private static final FileAccessorCache<ZipHandler, ZipFile> ourZipFileFileAccessorCache = new FileAccessorCache<ZipHandler, ZipFile>(20, 10) {
    @NotNull
    @Override
    protected ZipFile createAccessor(ZipHandler handler) throws IOException {
      final String canonicalPathToZip = handler.getCanonicalPathToZip();
      setFileAttributes(handler, canonicalPathToZip);

      return new ZipFile(canonicalPathToZip);
    }

    @Override
    protected void disposeAccessor(@NotNull final ZipFile fileAccessor) throws IOException {
      // todo: ZipFile isn't disposable for Java6, replace the code below with 'disposeCloseable(fileAccessor);'
      fileAccessor.close();
    }

    @Override
    public boolean isEqual(ZipHandler val1, ZipHandler val2) {
      return val1 == val2; // reference equality to handle different jars for different ZipHandlers on the same path
    }
  };

  protected static synchronized void setFileAttributes(@NotNull ZipHandler zipHandler, @NotNull String pathToZip) {
    FileAttributes attributes = FileSystemUtil.getAttributes(pathToZip);

    zipHandler.myFileStamp = attributes != null ? attributes.lastModified : DEFAULT_TIMESTAMP;
    zipHandler.myFileLength = attributes != null ? attributes.length : DEFAULT_LENGTH;
  }

  private static synchronized boolean isSameFileAttributes(@NotNull ZipHandler zipHandler, @NotNull FileAttributes attributes) {
    return attributes.lastModified == zipHandler.myFileStamp && attributes.length == zipHandler.myFileLength;
  }

  @NotNull
  private String getCanonicalPathToZip() throws IOException {
    String value = myCanonicalPathToZip;
    if (value == null) {
      myCanonicalPathToZip = value = getFileToUse().getCanonicalPath();
    }
    return value;
  }

  @Contract("true -> !null")
  protected FileAccessorCache.Handle<ZipFile> getCachedZipFileHandle(boolean createIfNeeded) throws IOException {
    try {
      FileAccessorCache.Handle<ZipFile> handle = createIfNeeded ? ourZipFileFileAccessorCache.get(this) : ourZipFileFileAccessorCache.getIfCached(this);

      // check handle is valid
      if (handle != null && getFile() == getFileToUse()) { // files are canonicalized
        // IDEA-148458, http://bugs.java.com/view_bug.do?bug_id=4425695, JVM crashes on use of opened ZipFile after it was updated
        // Reopen file if the file has been changed
        FileAttributes attributes = FileSystemUtil.getAttributes(getCanonicalPathToZip());
        if (attributes == null) {
          throw new FileNotFoundException(getCanonicalPathToZip());
        }

        if (isSameFileAttributes(this, attributes)) return handle;

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
  protected void clearCaches() {
    ourZipFileFileAccessorCache.remove(this);
    super.clearCaches();
  }

  @NotNull
  protected File getFileToUse() {
    return getFile();
  }

  @Override
  protected long getEntryFileStamp() {
    return myFileStamp;
  }

  @Override
  @NotNull
  protected ResourceHandle<ZipFile> acquireZipHandle() throws IOException {
    return getCachedZipFileHandle(true);
  }

  // also used in Kotlin
  public static void clearFileAccessorCache() {
    ourZipFileFileAccessorCache.clear();
  }
}