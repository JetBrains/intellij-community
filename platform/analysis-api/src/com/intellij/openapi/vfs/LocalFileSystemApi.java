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
 */
public class LocalFileSystemApi extends LocalFileSystem {
  private static class LocalFileSystemHolder {
    private static final LocalFileSystem ourRealInstance = (LocalFileSystem)VirtualFileManager.getInstance().getFileSystem(PROTOCOL);
  }

  @NotNull
  private static LocalFileSystem getRealInstance() {
    return LocalFileSystemHolder.ourRealInstance;
  }

  @Nullable
  @Override
  public VirtualFile findFileByPathIfCached(@NotNull String path) {
    //TODO[jo]: should we delegate it to VirtualFileLookup?
    return getRealInstance().findFileByPathIfCached(path);
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

  @NotNull
  @Override
  protected String extractRootPath(@NotNull String normalizedPath) {
    throw new RuntimeException("Must not be called on " + getClass().getName());
  }

  @Override
  @Nullable
  public String normalize(@NotNull String path) {
    throw new RuntimeException("Must not be called on " + getClass().getName());
  }

  @Override
  public int getRank() {
    return getRealInstance().getRank();
  }

  @Override
  public @NotNull VirtualFile copyFile(Object requestor,
                                       @NotNull VirtualFile file,
                                       @NotNull VirtualFile newParent,
                                       @NotNull String copyName) {
    throw new RuntimeException("Must not be called on " + getClass().getName());
  }

  @Override
  public @NotNull VirtualFile createChildDirectory(Object requestor, @NotNull VirtualFile parent, @NotNull String dir) {
    throw new RuntimeException("Must not be called on " + getClass().getName());
  }

  @Override
  public @NotNull VirtualFile createChildFile(Object requestor, @NotNull VirtualFile parent, @NotNull String file) {
    throw new RuntimeException("Must not be called on " + getClass().getName());
  }

  @Override
  public void deleteFile(Object requestor, @NotNull VirtualFile file) throws IOException {
    throw new RuntimeException("Must not be called on " + getClass().getName());
  }

  @Override
  public void moveFile(Object requestor, @NotNull VirtualFile file, @NotNull VirtualFile newParent) throws IOException {
    throw new RuntimeException("Must not be called on " + getClass().getName());
  }

  @Override
  public void renameFile(Object requestor, @NotNull VirtualFile file, @NotNull String newName) throws IOException {
    throw new RuntimeException("Must not be called on " + getClass().getName());
  }

  @Override
  public @Nullable FileAttributes getAttributes(@NotNull VirtualFile file) {
    throw new RuntimeException("Must not be called on " + getClass().getName());
  }

  @Override
  public @NotNull String getProtocol() {
    return getRealInstance().getProtocol();
  }

  @Override
  public void refresh(boolean asynchronous) {
    getRealInstance().refresh(asynchronous);
  }

  @Override
  public boolean exists(@NotNull VirtualFile file) {
    throw new RuntimeException("Must not be called on " + getClass().getName());
  }

  @Override
  public String @NotNull [] list(@NotNull VirtualFile file) {
    throw new RuntimeException("Must not be called on " + getClass().getName());
  }

  @Override
  public boolean isDirectory(@NotNull VirtualFile file) {
    throw new RuntimeException("Must not be called on " + getClass().getName());
  }

  @Override
  public long getTimeStamp(@NotNull VirtualFile file) {
    throw new RuntimeException("Must not be called on " + getClass().getName());
  }

  @Override
  public void setTimeStamp(@NotNull VirtualFile file, long timeStamp) throws IOException {
    throw new RuntimeException("Must not be called on " + getClass().getName());
  }

  @Override
  public boolean isWritable(@NotNull VirtualFile file) {
    throw new RuntimeException("Must not be called on " + getClass().getName());
  }

  @Override
  public void setWritable(@NotNull VirtualFile file, boolean writableFlag) throws IOException {
    throw new RuntimeException("Must not be called on " + getClass().getName());
  }

  @Override
  public byte @NotNull [] contentsToByteArray(@NotNull VirtualFile file) throws IOException {
    throw new RuntimeException("Must not be called on " + getClass().getName());
  }

  @Override
  public @NotNull InputStream getInputStream(@NotNull VirtualFile file) throws IOException {
    throw new RuntimeException("Must not be called on " + getClass().getName());
  }

  @Override
  public @NotNull OutputStream getOutputStream(@NotNull VirtualFile file,
                                               Object requestor,
                                               long modStamp,
                                               long timeStamp) throws IOException {
    throw new RuntimeException("Must not be called on " + getClass().getName());
  }

  @Override
  public long getLength(@NotNull VirtualFile file) {
    throw new RuntimeException("Must not be called on " + getClass().getName());
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
  public boolean isSymLink(@NotNull VirtualFile file) {
    throw new RuntimeException("Must not be called on " + getClass().getName());
  }

  @Override
  public String resolveSymLink(@NotNull VirtualFile file) {
    throw new RuntimeException("Must not be called on " + getClass().getName());
  }

  @Override
  public boolean markNewFilesAsDirty() {
    throw new RuntimeException("Must not be called on " + getClass().getName());
  }

  @Override
  @NotNull
  public String getCanonicallyCasedName(@NotNull VirtualFile file) {
    throw new RuntimeException("Must not be called on " + getClass().getName());
  }

  @Override
  public boolean hasChildren(@NotNull VirtualFile file) {
    throw new RuntimeException("Must not be called on " + getClass().getName());
  }

  @Override
  @NotNull
  public String extractPresentableUrl(@NotNull String path) {
    throw new RuntimeException("Must not be called on " + getClass().getName());
  }

  @Override
  public boolean isCaseSensitive() {
    return getRealInstance().isCaseSensitive();
  }

  @Override
  public boolean isValidName(@NotNull String name) {
    throw new RuntimeException("Must not be called on " + getClass().getName());
  }

  @Override
  @Nullable
  public Path getNioPath(@NotNull VirtualFile file) {
    throw new RuntimeException("Must not be called on " + getClass().getName());
  }
}
