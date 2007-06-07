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
}