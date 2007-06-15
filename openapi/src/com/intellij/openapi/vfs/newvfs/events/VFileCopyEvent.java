/*
 * @author max
 */
package com.intellij.openapi.vfs.newvfs.events;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileSystem;
import org.jetbrains.annotations.NonNls;

public class VFileCopyEvent extends VFileEvent {
  private VirtualFile myFile;
  private VirtualFile myNewParent;
  private String myNewChildName;

  public VFileCopyEvent(final Object requestor, final VirtualFile file, final VirtualFile newParent, final String newChildName) {
    super(requestor, false);
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

  public String getPath() {
    return myNewParent.getPath() + "/" + myNewChildName;
  }

  public VirtualFileSystem getFileSystem() {
    return myFile.getFileSystem();
  }

  public boolean isValid() {
    return myFile.isValid() && myNewParent.findChild(myNewChildName) == null;
  }

  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final VFileCopyEvent event = (VFileCopyEvent)o;

    if (!myFile.equals(event.myFile)) return false;
    if (!myNewChildName.equals(event.myNewChildName)) return false;
    if (!myNewParent.equals(event.myNewParent)) return false;

    return true;
  }

  public int hashCode() {
    int result;
    result = myFile.hashCode();
    result = 31 * result + myNewParent.hashCode();
    result = 31 * result + myNewChildName.hashCode();
    return result;
  }
}