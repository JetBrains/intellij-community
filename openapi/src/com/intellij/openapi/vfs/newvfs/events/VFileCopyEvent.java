/*
 * @author max
 */
package com.intellij.openapi.vfs.newvfs.events;

import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NonNls;

public class VFileCopyEvent extends VFileEvent {
  private VirtualFile myFile;
  private VirtualFile myNewParent;
  private String myNewChildName;

  public VFileCopyEvent(final VirtualFile file, final VirtualFile newParent, final String newChildName) {
    super(false);
    myFile = file;
    myNewParent = newParent;
    myNewChildName = newChildName;
  }

  public VirtualFile getFile() {
    return myFile;
  }

  public VirtualFile getNewParent() {
    return myNewParent;
  }

  public String getNewChildName() {
    return myNewChildName;
  }

  @NonNls
  public String toString() {
    return "VfsEvent[copy " + myFile +" to " + myNewParent + " as " + myNewChildName +"]";
  }
}