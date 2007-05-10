package com.intellij.localvcs.core.revisions;

import com.intellij.localvcs.core.changes.Change;
import com.intellij.localvcs.core.tree.Entry;

import java.util.Collections;
import java.util.List;

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

  public List<Change> getSubsequentChanges() {
    return Collections.emptyList();
  }
}
