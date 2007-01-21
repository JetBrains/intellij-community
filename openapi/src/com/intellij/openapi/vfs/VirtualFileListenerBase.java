package com.intellij.openapi.vfs;

public class VirtualFileListenerBase implements VirtualFileListener{
  public void propertyChanged(VirtualFilePropertyEvent event) {}
  public void contentsChanged(VirtualFileEvent event) {}
  public void fileCreated(VirtualFileEvent event) {}
  public void fileDeleted(VirtualFileEvent event) {}
  public void fileMoved(VirtualFileMoveEvent event) {}
  public void beforePropertyChange(VirtualFilePropertyEvent event) {}
  public void beforeContentsChange(VirtualFileEvent event) {}
  public void beforeFileDeletion(VirtualFileEvent event) {}
  public void fileCopied(final VirtualFileCopyEvent event) {}

  public void beforeFileMovement(VirtualFileMoveEvent event) {}
}
