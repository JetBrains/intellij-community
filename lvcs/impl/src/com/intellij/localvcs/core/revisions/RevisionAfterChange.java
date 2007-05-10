package com.intellij.localvcs.core.revisions;

import com.intellij.localvcs.core.changes.Change;
import com.intellij.localvcs.core.changes.ChangeList;
import com.intellij.localvcs.core.tree.Entry;
import com.intellij.localvcs.core.tree.RootEntry;

public class RevisionAfterChange extends RevisionBeforeChange {
  public RevisionAfterChange(Entry e, RootEntry r, ChangeList cl, Change c) {
    super(e, r, cl, c);
  }

  @Override
  public String getCauseAction() {
    return myChange.getName();
  }

  @Override
  protected boolean includeMyChange() {
    return false;
  }
}
