/*
 * @author max
 */
package com.intellij.openapi.vfs;

import org.jetbrains.annotations.NotNull;

public abstract class DeprecatedVirtualFile extends VirtualFile{
  @NotNull
    public String getUrl() {
    return VirtualFileManager.constructUrl(getFileSystem().getProtocol(), getPath());
  }

  public boolean isInLocalFileSystem() {
    return getFileSystem() instanceof LocalFileSystem;
  }
}