package com.intellij.history.core.revisions;

import com.intellij.history.core.tree.Entry;

public class CurrentRevision extends Revision {
  private Entry myEntry;

  public CurrentRevision(Entry e) {
    myEntry = e;
  }

  @Override
  public long getTimestamp() {
    return myEntry.getTimestamp();
  }

  @Override
  public Entry getEntry() {
    return myEntry;
  }
}
