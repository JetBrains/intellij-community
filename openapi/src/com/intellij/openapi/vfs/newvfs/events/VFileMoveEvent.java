/*
 * @author max
 */
package com.intellij.openapi.vfs.newvfs.events;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileSystem;
import org.jetbrains.annotations.NonNls;

public class VFileMoveEvent extends VFileEvent {
  private VirtualFile myFile;
  private VirtualFile myOldParent;
  private VirtualFile myNewParent;

  public VFileMoveEvent(Object requestor, final VirtualFile file, final VirtualFile newParent) {
    super(requestor, false);
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

  public String getPath() {
    return myFile.getPath();
  }

  public VirtualFileSystem getFileSystem() {
    return myFile.getFileSystem();
  }

  public boolean isValid() {
    return myFile.isValid() && myFile.getParent() == myOldParent && myOldParent.isValid();
  }
}