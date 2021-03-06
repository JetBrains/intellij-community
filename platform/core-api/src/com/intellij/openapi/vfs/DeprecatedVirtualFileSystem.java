// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.vfs.impl.BulkVirtualFileListenerAdapter;
import com.intellij.util.EventDispatcher;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

public abstract class DeprecatedVirtualFileSystem extends VirtualFileSystem {
  private final EventDispatcher<VirtualFileListener> myEventDispatcher = EventDispatcher.create(VirtualFileListener.class);

  protected void startEventPropagation() {
    Application app = ApplicationManager.getApplication();
    if (app != null) {
      app.getMessageBus().connect().subscribe(
        VirtualFileManager.VFS_CHANGES, new BulkVirtualFileListenerAdapter(myEventDispatcher.getMulticaster(), this));
    }
  }

  @Override
  public void addVirtualFileListener(@NotNull VirtualFileListener listener) {
    myEventDispatcher.addListener(listener);
  }

  @Override
  public void removeVirtualFileListener(@NotNull VirtualFileListener listener) {
    myEventDispatcher.removeListener(listener);
  }

  protected void firePropertyChanged(Object requestor,
                                     @NotNull VirtualFile file,
                                     @NotNull String propertyName,
                                     Object oldValue,
                                     Object newValue) {
    assertWriteAccessAllowed();
    VirtualFilePropertyEvent event = new VirtualFilePropertyEvent(requestor, file, propertyName, oldValue, newValue);
    myEventDispatcher.getMulticaster().propertyChanged(event);
  }

  protected void fireContentsChanged(Object requestor, @NotNull VirtualFile file, long oldModificationStamp) {
    assertWriteAccessAllowed();
    VirtualFileEvent event = new VirtualFileEvent(requestor, file, file.getParent(), oldModificationStamp, file.getModificationStamp());
    myEventDispatcher.getMulticaster().contentsChanged(event);
  }

  protected void fireFileCreated(@Nullable Object requestor, @NotNull VirtualFile file) {
    assertWriteAccessAllowed();
    VirtualFileEvent event = new VirtualFileEvent(requestor, file, file.getParent(), 0, 0);
    myEventDispatcher.getMulticaster().fileCreated(event);
  }

  protected void fireFileDeleted(Object requestor, @NotNull VirtualFile file, @NotNull String fileName, VirtualFile parent) {
    assertWriteAccessAllowed();
    VirtualFileEvent event = new VirtualFileEvent(requestor, file, parent, 0, 0);
    myEventDispatcher.getMulticaster().fileDeleted(event);
  }

  protected void fireFileMoved(Object requestor, @NotNull VirtualFile file, VirtualFile oldParent) {
    assertWriteAccessAllowed();
    VirtualFileMoveEvent event = new VirtualFileMoveEvent(requestor, file, oldParent, file.getParent());
    myEventDispatcher.getMulticaster().fileMoved(event);
  }

  protected void fireFileCopied(@Nullable Object requestor, @NotNull VirtualFile originalFile, @NotNull VirtualFile createdFile) {
    assertWriteAccessAllowed();
    VirtualFileCopyEvent event = new VirtualFileCopyEvent(requestor, originalFile, createdFile);
    myEventDispatcher.getMulticaster().fileCopied(event);
  }

  protected void fireBeforePropertyChange(Object requestor,
                                          @NotNull VirtualFile file,
                                          @NotNull String propertyName,
                                          Object oldValue,
                                          Object newValue) {
    assertWriteAccessAllowed();
    VirtualFilePropertyEvent event = new VirtualFilePropertyEvent(requestor, file, propertyName, oldValue, newValue);
    myEventDispatcher.getMulticaster().beforePropertyChange(event);
  }

  protected void fireBeforeContentsChange(Object requestor, @NotNull VirtualFile file) {
    assertWriteAccessAllowed();
    VirtualFileEvent event = new VirtualFileEvent(requestor, file, file.getParent(), 0, 0);
    myEventDispatcher.getMulticaster().beforeContentsChange(event);
  }

  protected void fireBeforeFileDeletion(Object requestor, @NotNull VirtualFile file) {
    assertWriteAccessAllowed();
    VirtualFileEvent event = new VirtualFileEvent(requestor, file, file.getParent(), 0, 0);
    myEventDispatcher.getMulticaster().beforeFileDeletion(event);
  }

  protected void fireBeforeFileMovement(Object requestor, @NotNull VirtualFile file, VirtualFile newParent) {
    assertWriteAccessAllowed();
    VirtualFileMoveEvent event = new VirtualFileMoveEvent(requestor, file, file.getParent(), newParent);
    myEventDispatcher.getMulticaster().beforeFileMovement(event);
  }

  protected void assertWriteAccessAllowed() {
    ApplicationManager.getApplication().assertWriteAccessAllowed();
  }

  @Override
  public boolean isReadOnly() {
    return true;
  }

  @Override
  protected void deleteFile(Object requestor, @NotNull VirtualFile vFile) throws IOException {
    throw unsupported("deleteFile", vFile);
  }

  @Override
  protected void moveFile(Object requestor, @NotNull VirtualFile vFile, @NotNull VirtualFile newParent) throws IOException {
    throw unsupported("move", vFile);
  }

  @Override
  protected void renameFile(Object requestor, @NotNull VirtualFile vFile, @NotNull String newName) throws IOException {
    throw unsupported("renameFile", vFile);
  }

  @NotNull
  @Override
  public VirtualFile createChildFile(Object requestor, @NotNull VirtualFile vDir, @NotNull String fileName) throws IOException {
    throw unsupported("createChildFile", vDir);
  }

  @NotNull
  @Override
  public VirtualFile createChildDirectory(Object requestor, @NotNull VirtualFile vDir, @NotNull String dirName) throws IOException {
    throw unsupported("createChildDirectory", vDir);
  }

  @NotNull
  @Override
  public VirtualFile copyFile(Object requestor, @NotNull VirtualFile vFile, @NotNull VirtualFile newParent, @NotNull String copyName) throws IOException {
    throw unsupported("copyFile", vFile);
  }

  private UnsupportedOperationException unsupported(String op, VirtualFile vFile) {
    return new UnsupportedOperationException(op + '(' + vFile + ") not supported by " + getClass().getName());
  }
}