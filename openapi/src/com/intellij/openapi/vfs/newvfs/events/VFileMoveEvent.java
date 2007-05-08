/*
 * @author max
 */
package com.intellij.openapi.vfs.newvfs.events;

import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NonNls;

public class VFileMoveEvent extends VFileEvent {
  private VirtualFile myFile;
  private VirtualFile myOldParent;
  private VirtualFile myNewParent;

  public VFileMoveEvent(final VirtualFile file, final VirtualFile newParent) {
    super(false);
    myFile = file;
    myNewParent = newParent;
    myOldParent = file.getParent();
  }

  public VirtualFile getFile() {
    return myFile;
  }

  public VirtualFile getNewParent() {
    return myNewParent;
  }

  public VirtualFile getOldParent() {
    return myOldParent;
  }

  @NonNls
  public String toString() {
    return "VfsEvent[move " + myFile.getName() +" from " + myOldParent + " to " + myNewParent + "]";
  }
}