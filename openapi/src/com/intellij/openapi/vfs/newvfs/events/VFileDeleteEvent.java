/*
 * @author max
 */
package com.intellij.openapi.vfs.newvfs.events;

import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NonNls;

public class VFileDeleteEvent extends VFileEvent {
  private VirtualFile myFile;

  public VFileDeleteEvent(final VirtualFile file, boolean isFromRefresh) {
    super(isFromRefresh);
    myFile = file;
  }

  public VirtualFile getFile() {
    return myFile;
  }

  @NonNls
  public String toString() {
    return "VfsEvent[deleted: " + myFile.getUrl() + "]";
  }
}