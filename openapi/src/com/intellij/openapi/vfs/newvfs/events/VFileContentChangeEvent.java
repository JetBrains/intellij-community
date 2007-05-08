/*
 * @author max
 */
package com.intellij.openapi.vfs.newvfs.events;

import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NonNls;

public class VFileContentChangeEvent extends VFileEvent {
  private VirtualFile myFile;
  private final long myOldTimestamp;
  private final long myNewTimestamp;

  public VFileContentChangeEvent(final VirtualFile file, long oldTimestamp, long newTimestamp, boolean isFromRefresh) {
    super(isFromRefresh);
    myFile = file;
    myOldTimestamp = oldTimestamp;
    myNewTimestamp = newTimestamp;
  }

  public VirtualFile getFile() {
    return myFile;
  }

  public long getNewTimestamp() {
    return myNewTimestamp;
  }

  public long getOldTimestamp() {
    return myOldTimestamp;
  }

  @NonNls
  public String toString() {
    return "VfsEvent[update: " + myFile.getUrl() + "]";
  }
}