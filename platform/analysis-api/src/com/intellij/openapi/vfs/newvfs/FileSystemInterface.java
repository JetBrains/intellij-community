// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs;

import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collections;

public interface FileSystemInterface {
  // default values for missing files (same as in corresponding java.io.File methods)
  long DEFAULT_LENGTH = 0;
  long DEFAULT_TIMESTAMP = 0;

  boolean exists(@NotNull VirtualFile file);

  String @NotNull [] list(@NotNull VirtualFile file);

  boolean isDirectory(@NotNull VirtualFile file);

  long getTimeStamp(@NotNull VirtualFile file);

  void setTimeStamp(@NotNull VirtualFile file, long timeStamp) throws IOException;

  boolean isWritable(@NotNull VirtualFile file);

  void setWritable(@NotNull VirtualFile file, boolean writableFlag) throws IOException;

  boolean isSymLink(@NotNull VirtualFile file);

  @Nullable String resolveSymLink(@NotNull VirtualFile file);

  /**
   * Returns all virtual files under which the given path is known in the VFS, starting with virtual file for the passed path.
   * Please note, that it is guaranteed to find all aliases only if path is canonical.
   */
  default @Unmodifiable @NotNull Iterable<@NotNull VirtualFile> findCachedFilesForPath(@NotNull String path) {
    return Collections.emptyList();
  }

  @NotNull VirtualFile createChildDirectory(@Nullable Object requestor, @NotNull VirtualFile parent, @NotNull String dir)
    throws IOException;

  @NotNull VirtualFile createChildFile(@Nullable Object requestor, @NotNull VirtualFile parent, @NotNull String file) throws IOException;

  void deleteFile(Object requestor, @NotNull VirtualFile file) throws IOException;

  void moveFile(Object requestor, @NotNull VirtualFile file, @NotNull VirtualFile newParent) throws IOException;

  void renameFile(Object requestor, @NotNull VirtualFile file, @NotNull String newName) throws IOException;

  @NotNull VirtualFile copyFile(Object requestor, @NotNull VirtualFile file, @NotNull VirtualFile newParent, @NotNull String copyName)
    throws IOException;

  /**
   * Returns content of a virtual file as a byte array. File content can be obtained eiter from VFS cache or real file system. Side
   * effect of this method is that the content may be cached in the VFS cache.
   *
   * @see com.intellij.openapi.vfs.newvfs.persistent.PersistentFS#contentsToByteArray(VirtualFile, boolean)
   */
  byte @NotNull [] contentsToByteArray(@NotNull VirtualFile file) throws IOException;

  /** Does NOT strip the BOM from the beginning of the stream, unlike the {@link VirtualFile#getInputStream()} */
  @NotNull InputStream getInputStream(@NotNull VirtualFile file) throws IOException;

  /** Does NOT add the BOM to the beginning of the stream, unlike the {@link VirtualFile#getOutputStream(Object)} */
  @NotNull OutputStream getOutputStream(@NotNull VirtualFile file, Object requestor, long modStamp, long timeStamp) throws IOException;

  /** @return length of the given file, in bytes, or 0 if the file is not a regular file */
  long getLength(@NotNull VirtualFile file);
}