// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs;

import com.intellij.openapi.util.io.FileAttributes;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileListener;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.VirtualFileSystem;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public abstract class NewVirtualFileSystem extends VirtualFileSystem implements FileSystemInterface, CachingVirtualFileSystem {
  private final Map<VirtualFileListener, VirtualFileListener> myListenerWrappers = new ConcurrentHashMap<>();

  /**
   * <p>Implementations <b>should</b> convert separator chars to forward slashes and remove duplicates ones,
   * and convert paths to "absolute" form (so that they start from a root that is valid for this FS and
   * could be later extracted with {@link #extractRootPath}).</p>
   *
   * <p>Implementations <b>should not</b> normalize paths by eliminating directory traversals or other indirections.</p>
   *
   * @return a normalized path, or {@code null} when a path is invalid for this FS.
   */
  @ApiStatus.OverrideOnly
  protected @Nullable String normalize(@NotNull String path) {
    return path;
  }

  /**
   * IntelliJ platform calls this method with non-null value returned by {@link #normalize}, but if something went wrong
   * and an implementation can't extract a valid root path nevertheless, it should return an empty string.
   */
  @ApiStatus.OverrideOnly
  protected abstract @NotNull String extractRootPath(@NotNull String normalizedPath);

  public abstract @Nullable VirtualFile findFileByPathIfCached(@NonNls @NotNull String path);

  @Override
  public void refreshWithoutFileWatcher(boolean asynchronous) {
    refresh(asynchronous);
  }

  @Override
  public boolean isReadOnly() {
    return true;
  }

  @Override
  public boolean isSymLink(@NotNull VirtualFile file) {
    return false;
  }

  @Override
  public String resolveSymLink(@NotNull VirtualFile file) {
    return null;
  }

  @Override
  public void addVirtualFileListener(@NotNull VirtualFileListener listener) {
    VirtualFileListener wrapper = new VirtualFileFilteringListener(listener, this);
    //noinspection deprecation
    VirtualFileManager.getInstance().addVirtualFileListener(wrapper);
    myListenerWrappers.put(listener, wrapper);
  }

  @Override
  public void removeVirtualFileListener(@NotNull VirtualFileListener listener) {
    VirtualFileListener wrapper = myListenerWrappers.remove(listener);
    if (wrapper != null) {
      //noinspection deprecation
      VirtualFileManager.getInstance().removeVirtualFileListener(wrapper);
    }
  }

  public abstract int getRank();

  @Override
  public abstract @NotNull VirtualFile copyFile(Object requestor, @NotNull VirtualFile file, @NotNull VirtualFile newParent, @NotNull String newName) throws IOException;

  @Override
  public abstract @NotNull VirtualFile createChildDirectory(Object requestor, @NotNull VirtualFile parent, @NotNull String name) throws IOException;

  @Override
  public abstract @NotNull VirtualFile createChildFile(Object requestor, @NotNull VirtualFile parent, @NotNull String name) throws IOException;

  @Override
  public abstract void deleteFile(Object requestor, @NotNull VirtualFile file) throws IOException;

  @Override
  public abstract void moveFile(Object requestor, @NotNull VirtualFile file, @NotNull VirtualFile newParent) throws IOException;

  @Override
  public abstract void renameFile(Object requestor, @NotNull VirtualFile file, @NotNull String newName) throws IOException;

  public boolean markNewFilesAsDirty() {
    return false;
  }

  //MAYBE RC: change signature (file) -> (parent, fileName)? no need to create a FakeVirtualFile
  public @NotNull String getCanonicallyCasedName(@NotNull VirtualFile file) {
    return file.getName();
  }

  /**
   * Reads various file attributes in one shot (to reduce the number of native I/O calls).
   *
   * @param file file to get attributes of.
   * @return attributes of a given file, or {@code null} if the file doesn't exist.
   */
  public abstract @Nullable FileAttributes getAttributes(@NotNull VirtualFile file);

  /**
   * Returns {@code true} if {@code path} represents a directory with at least one child.
   * Override if your file system can answer this question more efficiently (without listing all children).
   */
  public boolean hasChildren(@NotNull VirtualFile file) {
    return list(file).length != 0;
  }

  @ApiStatus.Internal
  public static @Nullable String normalizePath(@NotNull NewVirtualFileSystem vfs, @NotNull String path) {
    return vfs.normalize(path);
  }

  @ApiStatus.Internal
  public static @NotNull String extractRootPath(@NotNull NewVirtualFileSystem vfs, String normalizedPath) {
    return vfs.extractRootPath(normalizedPath);
  }
}
