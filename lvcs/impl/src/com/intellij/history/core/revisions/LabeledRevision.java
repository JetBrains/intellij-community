package com.intellij.history.core.revisions;

import com.intellij.history.core.changes.Change;
import com.intellij.history.core.changes.ChangeList;
import com.intellij.history.core.tree.Entry;

public class LabeledRevision extends RevisionAfterChange {
  public LabeledRevision(Entry e, Entry r, ChangeList cl, Change c) {
    super(e, r, cl, c);
  }

  @Override
  public String getName() {
    return myChange.getName();
  }

  @Override
  public String getCauseChangeName() {
    return null;
  }

  @Override
  public boolean wasChanged() {
    return false;
  }
}