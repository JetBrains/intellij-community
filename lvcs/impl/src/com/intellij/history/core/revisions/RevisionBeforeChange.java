package com.intellij.history.core.revisions;

import com.intellij.history.core.changes.Change;
import com.intellij.history.core.changes.ChangeList;
import com.intellij.history.core.changes.ChangeSet;
import com.intellij.history.core.tree.Entry;

public class RevisionBeforeChange extends Revision {
  protected Entry myEntry;
  protected Entry myRoot;
  protected ChangeList myChangeList;
  protected Change myChange;

  public RevisionBeforeChange(Entry e, Entry r, ChangeList cl, Change c) {
    myEntry = e;
    myRoot = r;
    myChangeList = cl;
    myChange = c;
  }

  @Override
  public long getTimestamp() {
    return myChange.getTimestamp();
  }

  @Override
  public Entry getEntry() {
    Entry rootCopy = myRoot.copy();
    myChangeList.revertUpTo(rootCopy, myChange, includeMyChange());
    return rootCopy.getEntry(myEntry.getId());
  }

  @Override
  public boolean isBefore(ChangeSet c) {
    return myChangeList.isBefore(myChange, c, includeMyChange());
  }

  protected boolean includeMyChange() {
    return true;
  }
}