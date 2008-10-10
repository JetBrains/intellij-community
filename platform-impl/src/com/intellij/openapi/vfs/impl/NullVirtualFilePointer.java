package com.intellij.openapi.vfs.impl;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.pointers.VirtualFilePointer;
import org.jetbrains.annotations.NotNull;

/**
 * @author cdr
 */
class NullVirtualFilePointer implements VirtualFilePointer {
  public static final VirtualFilePointer INSTANCE = new NullVirtualFilePointer();
  @NotNull
  public String getFileName() {
    return "NullVirtualFilePointer";
  }

  public VirtualFile getFile() {
    return null;
  }

  @NotNull
  public String getUrl() {
    return getFileName();
  }

  @NotNull
  public String getPresentableUrl() {
    return getUrl();
  }

  public boolean isValid() {
    return true;
  }
}
