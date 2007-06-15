/*
 * @author max
 */
package com.intellij.openapi.vfs.newvfs.events;

import com.intellij.openapi.vfs.VirtualFileSystem;

public abstract class VFileEvent {
  private boolean myIsFromRefresh;
  private Object myRequestor;

  public VFileEvent(Object requestor, final boolean isFromRefresh) {
    myRequestor = requestor;
    myIsFromRefresh = isFromRefresh;
  }

  public boolean isFromRefresh() {
    return myIsFromRefresh;
  }

  public Object getRequestor() {
    return myRequestor;
  }

  public abstract String getPath();

  public abstract VirtualFileSystem getFileSystem();

  public abstract boolean isValid();

  public abstract int hashCode();
  public abstract boolean equals(Object o);
}