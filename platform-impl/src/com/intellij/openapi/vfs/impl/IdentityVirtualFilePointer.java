package com.intellij.openapi.vfs.impl;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.pointers.VirtualFilePointer;
import org.jetbrains.annotations.NotNull;

/**
 * @author cdr
 */
class IdentityVirtualFilePointer implements VirtualFilePointer {
  private final VirtualFile myFile;

  IdentityVirtualFilePointer(@NotNull VirtualFile file) {
    myFile = file;
  }

  @NotNull
  public String getFileName() {
    return myFile.getName();
  }

  public VirtualFile getFile() {
    return myFile;
  }

  @NotNull
  public String getUrl() {
    return myFile.getUrl();
  }

  @NotNull
  public String getPresentableUrl() {
    return getUrl();
  }

  public boolean isValid() {
    return myFile.isValid();
  }
}