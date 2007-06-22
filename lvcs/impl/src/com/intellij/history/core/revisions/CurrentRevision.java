package com.intellij.history.core.revisions;

import com.intellij.history.core.tree.Entry;

public class CurrentRevision extends Revision {
  private Entry myEntry;
  private long myTimestamp;

  public CurrentRevision(Entry e, long timestamp) {
    myEntry = e;
    myTimestamp = timestamp;
  }

  @Override
  public long getTimestamp() {
    return myTimestamp;
  }

  @Override
  public Entry getEntry() {
    return myEntry;
  }
}
