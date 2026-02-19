// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.ex.dummy;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileSystem;
import org.jetbrains.annotations.NotNull;

abstract class DummyFileBase extends VirtualFile {
  private final DummyFileSystem myFileSystem;
  private final DummyDirectoryImpl myParent;
  private String myName;
  protected boolean myIsValid = true;

  DummyFileBase(@NotNull DummyFileSystem fileSystem, DummyDirectoryImpl parent, @NotNull String name) {
    myFileSystem = fileSystem;
    myParent = parent;
    myName = name;
  }

  @Override
  public @NotNull VirtualFileSystem getFileSystem() {
    return myFileSystem;
  }

  @Override
  public @NotNull String getPath() {
    if (myParent == null) {
      return myName;
    }
    return myParent.getPath() + "/" + myName;
  }

  @Override
  public @NotNull String getName() {
    return myName;
  }

  void setName(@NotNull String name) {
    myName = name;
  }

  @Override
  public boolean isWritable() {
    return true;
  }

  @Override
  public boolean isValid() {
    return myIsValid;
  }

  @Override
  public VirtualFile getParent() {
    return myParent;
  }

  @Override
  public long getTimeStamp() {
    return -1;
  }

  @Override
  public void refresh(boolean asynchronous, boolean recursive, Runnable postRunnable) {
  }
}
