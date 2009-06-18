package com.intellij.openapi.vfs.impl;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.pointers.VirtualFilePointer;
import org.jetbrains.annotations.NotNull;

/**
 * @author cdr
 */
class NullVirtualFilePointer implements VirtualFilePointer {
  private final String myUrl;

  NullVirtualFilePointer(@NotNull String url) {
    myUrl = url;
  }

  @NotNull
  public String getFileName() {
    return getUrl();
  }

  public VirtualFile getFile() {
    return null;
  }

  @NotNull
  public String getUrl() {
    return myUrl;
  }

  @NotNull
  public String getPresentableUrl() {
    return getUrl();
  }

  public boolean isValid() {
    return true;
  }
}
