package com.intellij.openapi.vfs;

import org.jetbrains.annotations.NotNull;

/**
 * Provides data for event which is fired when a virtual file is copied.
 *
 * @see com.intellij.openapi.vfs.VirtualFileListener#fileCopied(com.intellij.openapi.vfs.VirtualFileCopyEvent)
 */
public class VirtualFileCopyEvent extends VirtualFileEvent {
  private final VirtualFile myOriginalFile;

  public VirtualFileCopyEvent(Object requestor, VirtualFile original, VirtualFile created){
    super(requestor, created, original.getName(), created.getParent());
    myOriginalFile = created;
  }

  /**
   * Returns original file.
   *
   * @return original file.
   */
  @NotNull
  public VirtualFile getOriginalFile() {
    return myOriginalFile;
  }
}
