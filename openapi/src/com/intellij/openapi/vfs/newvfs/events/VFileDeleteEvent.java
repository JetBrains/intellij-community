/*
 * @author max
 */
package com.intellij.openapi.vfs.newvfs.events;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileSystem;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class VFileDeleteEvent extends VFileEvent {
  private @NotNull final VirtualFile myFile;
  private int myDepth = -1;

  public VFileDeleteEvent(@Nullable Object requestor, @NotNull VirtualFile file, boolean isFromRefresh) {
    super(requestor, isFromRefresh);
    myFile = file;
  }

  @NotNull
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

  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final VFileDeleteEvent event = (VFileDeleteEvent)o;

    return myFile.equals(event.myFile);
  }

  public int hashCode() {
    return myFile.hashCode();
  }

  public int getFileDepth() {
    if (myDepth == -1) {
      int d = 0;
      VirtualFile cur = myFile;
      while (cur != null) {
        d++;
        cur = cur.getParent();
      }
      myDepth = d;
    }

    return myDepth;
  }
}