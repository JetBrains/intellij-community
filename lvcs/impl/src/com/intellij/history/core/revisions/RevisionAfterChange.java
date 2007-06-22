package com.intellij.history.core.revisions;

import com.intellij.history.core.changes.Change;
import com.intellij.history.core.changes.ChangeList;
import com.intellij.history.core.tree.Entry;

public class RevisionAfterChange extends RevisionBeforeChange {
  public RevisionAfterChange(Entry e, Entry r, ChangeList cl, Change c) {
    super(e, r, cl, c);
  }

  @Override
  public Change getCauseChange() {
    return myChange;
  }

  @Override
  protected boolean includeMyChange() {
    return false;
  }
}
