/*
 * @author max
 */
package com.intellij.openapi.vfs.newvfs.events;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileSystem;
import com.intellij.util.LocalTimeCounter;
import org.jetbrains.annotations.NonNls;

public class VFileContentChangeEvent extends VFileEvent {
  private VirtualFile myFile;
  private final long myOldModificationStamp;
  private final long myNewModificationStamp;

  public VFileContentChangeEvent(final Object requestor, final VirtualFile file, long oldModificationStamp, long newModificationStamp, boolean isFromRefresh) {
    super(requestor, isFromRefresh);

    myFile = file;
    myOldModificationStamp = oldModificationStamp;
    myNewModificationStamp = newModificationStamp == -1 ? LocalTimeCounter.currentTime() : newModificationStamp;
  }

  public VirtualFile getFile() {
    return myFile;
  }

  public long getModificationStamp() {
    return myNewModificationStamp;
  }

  public long getOldModificationStamp() {
    return myOldModificationStamp;
  }

  @NonNls
  public String toString() {
    return "VfsEvent[update: " + myFile.getUrl() + "]";
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

  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final VFileContentChangeEvent event = (VFileContentChangeEvent)o;

    if (myNewModificationStamp != event.myNewModificationStamp) return false;
    if (myOldModificationStamp != event.myOldModificationStamp) return false;
    if (!myFile.equals(event.myFile)) return false;

    return true;
  }

  public int hashCode() {
    int result;
    result = myFile.hashCode();
    result = 31 * result + (int)(myOldModificationStamp ^ (myOldModificationStamp >>> 32));
    result = 31 * result + (int)(myNewModificationStamp ^ (myNewModificationStamp >>> 32));
    return result;
  }
}