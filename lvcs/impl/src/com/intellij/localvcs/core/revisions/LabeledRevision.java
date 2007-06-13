package com.intellij.localvcs.core.revisions;

import com.intellij.localvcs.core.changes.Change;
import com.intellij.localvcs.core.changes.ChangeList;
import com.intellij.localvcs.core.tree.Entry;

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

  @Override
  public boolean isMarked() {
    return myChange.isMark();
  }
}