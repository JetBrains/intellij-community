/*
 * @author max
 */
package com.intellij.openapi.vfs.newvfs.events;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileSystem;
import org.jetbrains.annotations.NonNls;

public class VFileDeleteEvent extends VFileEvent {
  private VirtualFile myFile;

  public VFileDeleteEvent(Object requestor, final VirtualFile file, boolean isFromRefresh) {
    super(requestor, isFromRefresh);
    myFile = file;
  }

  public VirtualFile getFile() {
    return myFile;
  }

  @NonNls
  public String toString() {
    return "VfsEvent[deleted: " + myFile.getUrl() + "]";
  }

  public String getPath() {
    return myFile.getPath();
  }

  public VirtualFileSystem getFileSystem() {
    return myFile.getFileSystem();
  }

  public boolean isValid() {
    return myFile.isValid();
  }
}