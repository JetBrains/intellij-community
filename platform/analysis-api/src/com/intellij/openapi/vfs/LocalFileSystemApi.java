// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs;

import com.intellij.openapi.util.io.FileAttributes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Set;

/**
 * We have two roles behind the {@link LocalFileSystem} class so far.
 * The first role - is an API entry point to exchange some local files
 * with a {@link VirtualFile}. Secondly, it is implementation of the
 * filesystem itself used from {@link VirtualFile} implementations.
 * <br />
 * We'd like to provide a transparent {@link VirtualFileLookupService}
 * to implement all major VirtualFile lookup needs from one hand. From
 * the other hand, we'd like to separate platform service from the
 * filesystem implementation.
 * <br />
 * This class is a fake implementation of the {@link LocalFileSystem}
 * that delegates to the right underlying services for most of the methods.
 * It throws exceptions when {@link VirtualFile} specific APIs are called
 * directly. This class must not be used with {@link VfsImplUtil} or any other
 * vfs-impl classes.
 * <br />
 * @implNote Calling base methods that are not overridden here will likely
 * throw an Non-Implemented exception as we keep only API methods here,
 * where as all {@link VirtualFile} implementation related methods are
 * not listed
 */
public abstract class LocalFileSystemApi extends LocalFileSystem {
  @NotNull
  protected abstract LocalFileSystem getRealInstance();

  @Nullable
  @Override
  public VirtualFile findFileByPathIfCached(@NotNull String path) {
    return VirtualFileLookup.newLookup().onlyIfCached().fromPath(path);
  }

  @Nullable
  @Override
  public VirtualFile findFileByPath(@NotNull String path) {
    return VirtualFileLookup.newLookup().fromPath(path);
  }

  @Nullable
  @Override
  public VirtualFile refreshAndFindFileByPath(@NotNull String path) {
    return VirtualFileLookup.newLookup().withRefresh().fromPath(path);
  }

  @Nullable
  @Override
  public VirtualFile findFileByIoFile(@NotNull File file) {
    return VirtualFileLookup.newLookup().fromIoFile(file);
  }

  @Nullable
  @Override
  public VirtualFile refreshAndFindFileByIoFile(@NotNull File file) {
    return VirtualFileLookup.newLookup().withRefresh().fromIoFile(file);
  }

  @Override
  public void refreshIoFiles(@NotNull Iterable<? extends File> files) {
    getRealInstance().refreshIoFiles(files);
  }

  @Override
  public void refreshIoFiles(@NotNull Iterable<? extends File> files, boolean async, boolean recursive, @Nullable Runnable onFinish) {
    getRealInstance().refreshIoFiles(files, async, recursive, onFinish);
  }

  @Override
  public void refreshFiles(@NotNull Iterable<? extends VirtualFile> files) {
    getRealInstance().refreshFiles(files);
  }

  @Override
  public void refreshFiles(@NotNull Iterable<? extends VirtualFile> files, boolean async, boolean recursive, @Nullable Runnable onFinish) {
    getRealInstance().refreshFiles(files, async, recursive, onFinish);
  }

  @NotNull
  @Override
  public Set<WatchRequest> replaceWatchedRoots(@NotNull Collection<WatchRequest> watchRequests,
                                               @Nullable Collection<String> recursiveRoots,
                                               @Nullable Collection<String> flatRoots) {
    return getRealInstance().replaceWatchedRoots(watchRequests, recursiveRoots, flatRoots);
  }

  @Override
  public void registerAuxiliaryFileOperationsHandler(@NotNull LocalFileOperationsHandler handler) {
    getRealInstance().registerAuxiliaryFileOperationsHandler(handler);
  }

  @Override
  public void unregisterAuxiliaryFileOperationsHandler(@NotNull LocalFileOperationsHandler handler) {
    getRealInstance().unregisterAuxiliaryFileOperationsHandler(handler);
  }

  @Override
  public int getRank() {
    return getRealInstance().getRank();
  }

  @NotNull
  @Override
  public String getProtocol() {
    return getRealInstance().getProtocol();
  }

  @Override
  public void refresh(boolean asynchronous) {
    getRealInstance().refresh(asynchronous);
  }

  @Override
  public void refreshWithoutFileWatcher(boolean asynchronous) {
    getRealInstance().refreshWithoutFileWatcher(asynchronous);
  }

  @Override
  public boolean isReadOnly() {
    return getRealInstance().isReadOnly();
  }

  @Override
  public boolean isCaseSensitive() {
    return getRealInstance().isCaseSensitive();
  }
}
