package com.intellij.localvcs.core.revisions;

import com.intellij.localvcs.core.changes.ChangeSet;
import com.intellij.localvcs.core.tree.Entry;

public abstract class Revision {
  public String getName() {
    return null;
  }

  public abstract long getTimestamp();

  public String getCauseAction() {
    return null;
  }

  public abstract Entry getEntry();

  public Difference getDifferenceWith(Revision right) {
    Entry leftEntry = getEntry();
    Entry rightEntry = right.getEntry();

    return leftEntry.getDifferenceWith(rightEntry);
  }

  public boolean isMarked() {
    return false;
  }

  public boolean isBefore(ChangeSet c) {
    return false;
  }
}
