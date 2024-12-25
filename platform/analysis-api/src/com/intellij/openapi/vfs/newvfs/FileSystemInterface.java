// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.concurrency.annotations.RequiresReadLockAbsence;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collections;

/**
 * Methods of this interface are marked with {@code @RequiresReadLockAbsence} annotation (beware that this annotation also
 * implies 'requires _write_ lock absence').
 * This is because the methods may involve an IO, and it should be no IO under RA/WA.
 * <p>
 * This requirement should be considered 'strict' for the <b>new</b> usages, but legacy usage <b>could</b> continue use
 * (V)FS methods under RA/WA -- there are too many usages like that to fix all them, and some of them are quite hard to
 * fix because e.g. RA/WA is needed for PSI access around FS access.</p>
 */
public interface FileSystemInterface {
  // default values for missing files (same as in corresponding java.io.File methods)
  long DEFAULT_LENGTH = 0;
  long DEFAULT_TIMESTAMP = 0;

  @RequiresReadLockAbsence(generateAssertion = false)
  boolean exists(@NotNull VirtualFile file);

  @RequiresReadLockAbsence(generateAssertion = false)
  String @NotNull [] list(@NotNull VirtualFile file);

  @RequiresReadLockAbsence(generateAssertion = false)
  boolean isDirectory(@NotNull VirtualFile file);

  @RequiresReadLockAbsence(generateAssertion = false)
  long getTimeStamp(@NotNull VirtualFile file);
  @RequiresReadLockAbsence(generateAssertion = false)
  void setTimeStamp(@NotNull VirtualFile file, long timeStamp) throws IOException;

  @RequiresReadLockAbsence(generateAssertion = false)
  boolean isWritable(@NotNull VirtualFile file);
  @RequiresReadLockAbsence(generateAssertion = false)
  void setWritable(@NotNull VirtualFile file, boolean writableFlag) throws IOException;

  @RequiresReadLockAbsence(generateAssertion = false)
  boolean isSymLink(@NotNull VirtualFile file);
  
  @RequiresReadLockAbsence(generateAssertion = false)
  @Nullable String resolveSymLink(@NotNull VirtualFile file);

  /**
   * Returns all virtual files under which the given path is known in the VFS, starting with virtual file for the passed path.
   * Please note, that it is guaranteed to find all aliases only if path is canonical.
   */
  @RequiresReadLockAbsence(generateAssertion = false)
  default @Unmodifiable @NotNull Iterable<@NotNull VirtualFile> findCachedFilesForPath(@NotNull String path) {
    return Collections.emptyList();
  }

  @RequiresReadLockAbsence(generateAssertion = false)
  @NotNull VirtualFile createChildDirectory(@Nullable Object requestor, @NotNull VirtualFile parent, @NotNull String dir) throws IOException;
  @RequiresReadLockAbsence(generateAssertion = false)
  @NotNull VirtualFile createChildFile(@Nullable Object requestor, @NotNull VirtualFile parent, @NotNull String file) throws IOException;

  @RequiresReadLockAbsence(generateAssertion = false)
  void deleteFile(Object requestor, @NotNull VirtualFile file) throws IOException;
  @RequiresReadLockAbsence(generateAssertion = false)
  void moveFile(Object requestor, @NotNull VirtualFile file, @NotNull VirtualFile newParent) throws IOException;
  @RequiresReadLockAbsence(generateAssertion = false)
  void renameFile(Object requestor, @NotNull VirtualFile file, @NotNull String newName) throws IOException;

  @RequiresReadLockAbsence(generateAssertion = false)
  @NotNull VirtualFile copyFile(Object requestor, @NotNull VirtualFile file, @NotNull VirtualFile newParent, @NotNull String copyName) throws IOException;

  /**
   * Returns content of a virtual file as a byte array. File content can be obtained eiter from VFS cache or real file system. Side
   * effect of this method is that the content may be cached in the VFS cache.
   *
   * @see com.intellij.openapi.vfs.newvfs.persistent.PersistentFS#contentsToByteArray(VirtualFile, boolean)
   */
  @RequiresReadLockAbsence(generateAssertion = false)
  byte @NotNull [] contentsToByteArray(@NotNull VirtualFile file) throws IOException;

  /** Does NOT strip the BOM from the beginning of the stream, unlike the {@link VirtualFile#getInputStream()} */
  @RequiresReadLockAbsence(generateAssertion = false)
  @NotNull InputStream getInputStream(@NotNull VirtualFile file) throws IOException;

  /** Does NOT add the BOM to the beginning of the stream, unlike the {@link VirtualFile#getOutputStream(Object)} */
  @RequiresReadLockAbsence(generateAssertion = false)
  @NotNull OutputStream getOutputStream(@NotNull VirtualFile file, Object requestor, long modStamp, long timeStamp) throws IOException;

  @RequiresReadLockAbsence(generateAssertion = false)
  long getLength(@NotNull VirtualFile file);
}