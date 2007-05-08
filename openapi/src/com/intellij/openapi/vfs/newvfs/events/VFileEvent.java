/*
 * @author max
 */
package com.intellij.openapi.vfs.newvfs.events;

public class VFileEvent {
  private boolean myIsFromRefresh;

  public VFileEvent(final boolean isFromRefresh) {
    myIsFromRefresh = isFromRefresh;
  }

  public boolean isFromRefresh() {
    return myIsFromRefresh;
  }
}