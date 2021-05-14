// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing.events;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileWithId;
import com.intellij.testFramework.LightVirtualFile;
import org.jetbrains.annotations.NotNull;

public final class DeletedVirtualFileStub extends LightVirtualFile implements VirtualFileWithId {
  private final int myFileId;

  public DeletedVirtualFileStub(@NotNull VirtualFileWithId original) {
    setOriginalFile((VirtualFile)original);
    myFileId = original.getId();
  }

  @Override
  public int getId() {
    return myFileId;
  }

  @Override
  public boolean isValid() {
    return false;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    DeletedVirtualFileStub stub = (DeletedVirtualFileStub)o;
    return myFileId == stub.myFileId;
  }

  @Override
  public int hashCode() {
    return myFileId;
  }

  @Override
  public String toString() {
    VirtualFile originalFile = getOriginalFile();
    String fileText = originalFile == null
                      ? ("deleted in previous session (file id = " + myFileId + ")")
                      : originalFile.toString();
    return "invalidated file :" + fileText;
  }
}
