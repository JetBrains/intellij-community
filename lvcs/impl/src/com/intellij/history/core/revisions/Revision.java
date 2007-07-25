package com.intellij.history.core.revisions;

import com.intellij.history.core.changes.Change;
import com.intellij.history.core.changes.ChangeSet;
import com.intellij.history.core.tree.Entry;

public abstract class Revision {
  public String getName() {
    return null;
  }

  public abstract long getTimestamp();

  public String getCauseChangeName() {
    return getCauseChange() == null ? null : getCauseChange().getName();
  }

  public Change getCauseChange() {
    return null;
  }

  public abstract Entry getEntry();

  public Difference getDifferenceWith(Revision right) {
    Entry leftEntry = getEntry();
    Entry rightEntry = right.getEntry();

    return leftEntry.getDifferenceWith(rightEntry);
  }

  public boolean isImportant() {
    return true;
  }

  public boolean isBefore(ChangeSet c) {
    return false;
  }
}
