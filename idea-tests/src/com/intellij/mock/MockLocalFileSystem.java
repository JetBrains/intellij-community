/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.mock;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileOperationsHandler;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Processor;
import com.intellij.util.io.fs.IFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Set;

/**
 * @author nik
 */
public class MockLocalFileSystem extends LocalFileSystem {
  private MockVirtualFileSystem myDelegate = new MockVirtualFileSystem();

  @Nullable
  public VirtualFile findFileByIoFile(final File file) {
    return myDelegate.findFileByPath(FileUtil.toSystemIndependentName(file.getPath()));
  }

  @Nullable
  public VirtualFile findFileByIoFile(final IFile file) {
    return myDelegate.findFileByPath(FileUtil.toSystemIndependentName(file.getPath()));
  }

  @Nullable
  public VirtualFile refreshAndFindFileByIoFile(final File file) {
    return findFileByIoFile(file);
  }

  @Nullable
  public VirtualFile refreshAndFindFileByIoFile(final IFile ioFile) {
    return findFileByIoFile(ioFile);
  }

  public void refreshIoFiles(final Iterable<File> files) {
  }

  public void refreshFiles(final Iterable<VirtualFile> files) {
  }

  public byte[] physicalContentsToByteArray(final VirtualFile virtualFile) throws IOException {
    throw new UnsupportedOperationException("'physicalContentsToByteArray' not implemented in " + getClass().getName());
  }

  public long physicalLength(final VirtualFile virtualFile) throws IOException {
    throw new UnsupportedOperationException("'physicalLength' not implemented in " + getClass().getName());
  }

  @Nullable
  public WatchRequest addRootToWatch(final @NotNull String rootPath, final boolean toWatchRecursively) {
    throw new UnsupportedOperationException("'addRootToWatch' not implemented in " + getClass().getName());
  }

  @NotNull
  public Set<WatchRequest> addRootsToWatch(final @NotNull Collection<String> rootPaths, final boolean toWatchRecursively) {
    throw new UnsupportedOperationException("'addRootsToWatch' not implemented in " + getClass().getName());
  }

  public void removeWatchedRoots(final @NotNull Collection<WatchRequest> rootsToWatch) {
  }

  public void removeWatchedRoot(final @NotNull WatchRequest watchRequest) {
  }

  public void registerAuxiliaryFileOperationsHandler(final LocalFileOperationsHandler handler) {
  }

  public void unregisterAuxiliaryFileOperationsHandler(final LocalFileOperationsHandler handler) {
  }

  public boolean processCachedFilesInSubtree(final VirtualFile file, final Processor<VirtualFile> processor) {
    throw new UnsupportedOperationException("'processCachedFilesInSubtree' not implemented in " + getClass().getName());
  }

  public String getProtocol() {
    return LocalFileSystem.PROTOCOL;
  }

  @Nullable
  public VirtualFile findFileByPath(@NotNull @NonNls final String path) {
    return myDelegate.findFileByPath(path);
  }

  public void refresh(final boolean asynchronous) {
  }

  @Nullable
  public VirtualFile refreshAndFindFileByPath(final String path) {
    return findFileByPath(path);
  }

  public void forceRefreshFiles(final boolean asynchronous, @NotNull final VirtualFile... files) {
  }

  protected void deleteFile(final Object requestor, final VirtualFile vFile) throws IOException {
  }

  protected void moveFile(final Object requestor, final VirtualFile vFile, final VirtualFile newParent) throws IOException {
  }

  protected void renameFile(final Object requestor, final VirtualFile vFile, final String newName) throws IOException {
  }

  protected VirtualFile createChildFile(final Object requestor, final VirtualFile vDir, final String fileName) throws IOException {
    return myDelegate.createChildFile(requestor, vDir, fileName);
  }

  protected VirtualFile createChildDirectory(final Object requestor, final VirtualFile vDir, final String dirName) throws IOException {
    return myDelegate.createChildDirectory(requestor, vDir, dirName);
  }

  protected VirtualFile copyFile(final Object requestor, final VirtualFile virtualFile, final VirtualFile newParent, final String copyName)
    throws IOException {
    return myDelegate.copyFile(requestor, virtualFile, newParent, copyName);
  }
}
