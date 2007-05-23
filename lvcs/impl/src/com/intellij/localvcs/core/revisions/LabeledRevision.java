package com.intellij.localvcs.core.revisions;

import com.intellij.localvcs.core.changes.Change;
import com.intellij.localvcs.core.changes.ChangeList;
import com.intellij.localvcs.core.tree.Entry;
import com.intellij.localvcs.core.tree.RootEntry;

public class LabeledRevision extends RevisionAfterChange {
  public LabeledRevision(Entry e, RootEntry r, ChangeList cl, Change c) {
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
  public boolean isMarked() {
    return myChange.isMark();
  }
}