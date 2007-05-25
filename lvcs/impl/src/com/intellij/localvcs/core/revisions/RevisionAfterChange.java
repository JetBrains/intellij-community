package com.intellij.localvcs.core.revisions;

import com.intellij.localvcs.core.changes.Change;
import com.intellij.localvcs.core.changes.ChangeList;
import com.intellij.localvcs.core.tree.Entry;

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
