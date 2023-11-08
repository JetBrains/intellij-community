// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.ex.dummy;

import com.intellij.analysis.AnalysisBundle;
import com.intellij.openapi.vfs.DeprecatedVirtualFileSystem;
import com.intellij.openapi.vfs.NonPhysicalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

public class DummyFileSystem extends DeprecatedVirtualFileSystem implements NonPhysicalFileSystem {
  public static final @NonNls String PROTOCOL = "dummy";

  public static DummyFileSystem getInstance() {
    return (DummyFileSystem)VirtualFileManager.getInstance().getFileSystem(PROTOCOL);
  }

  public DummyFileSystem() {
    startEventPropagation();
  }

  public @NotNull VirtualFile createRoot(@NotNull String name) {
    DummyDirectoryImpl root = new DummyDirectoryImpl(this, null, name);
    fireFileCreated(null, root);
    return root;
  }

  @Override
  public @NotNull String getProtocol() {
    return PROTOCOL;
  }

  @Override
  public VirtualFile findFileByPath(@NotNull String path) {
//    LOG.error("method not implemented");
    return null;
  }

  @Override
  public @NotNull String extractPresentableUrl(@NotNull String path) {
    return path;
  }

  @Override
  public void refresh(boolean asynchronous) {
  }

  @Override
  public VirtualFile refreshAndFindFileByPath(@NotNull String path) {
    return findFileByPath(path);
  }

  @Override
  public void deleteFile(Object requestor, @NotNull VirtualFile vFile) throws IOException {
    fireBeforeFileDeletion(requestor, vFile);
    final DummyDirectoryImpl parent = (DummyDirectoryImpl)vFile.getParent();
    if (parent == null) {
      throw new IOException(AnalysisBundle.message("file.delete.root.error", vFile.getPresentableUrl()));
    }

    parent.removeChild((DummyFileBase)vFile);
    fireFileDeleted(requestor, vFile, vFile.getName(), parent);
  }

  @Override
  public void renameFile(Object requestor, @NotNull VirtualFile vFile, @NotNull String newName) {
    final String oldName = vFile.getName();
    fireBeforePropertyChange(requestor, vFile, VirtualFile.PROP_NAME, oldName, newName);
    ((DummyFileBase)vFile).setName(newName);
    firePropertyChanged(requestor, vFile, VirtualFile.PROP_NAME, oldName, newName);
  }

  @Override
  public @NotNull VirtualFile createChildFile(Object requestor, @NotNull VirtualFile vDir, @NotNull String fileName) throws IOException {
    final DummyDirectoryImpl dir = (DummyDirectoryImpl)vDir;
    DummyFileBase child = new DummyFileImpl(this, dir, fileName);
    dir.addChild(child);
    fireFileCreated(requestor, child);
    return child;
  }

  @Override
  public void fireBeforeContentsChange(final Object requestor, final @NotNull VirtualFile file) {
    super.fireBeforeContentsChange(requestor, file);
  }

  @Override
  public void fireContentsChanged(final Object requestor, final @NotNull VirtualFile file, final long oldModificationStamp) {
    super.fireContentsChanged(requestor, file, oldModificationStamp);
  }

  @Override
  public @NotNull VirtualFile createChildDirectory(Object requestor, @NotNull VirtualFile vDir, @NotNull String dirName) {
    final DummyDirectoryImpl dir = (DummyDirectoryImpl)vDir;
    DummyFileBase child = new DummyDirectoryImpl(this, dir, dirName);
    dir.addChild(child);
    fireFileCreated(requestor, child);
    return child;
  }
}
