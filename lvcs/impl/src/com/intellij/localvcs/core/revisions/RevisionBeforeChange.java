package com.intellij.localvcs.core.revisions;

import com.intellij.localvcs.core.changes.Change;
import com.intellij.localvcs.core.changes.ChangeList;
import com.intellij.localvcs.core.changes.ChangeSet;
import com.intellij.localvcs.core.tree.Entry;
import com.intellij.localvcs.core.tree.RootEntry;

import java.util.ArrayList;
import java.util.List;

public class RevisionBeforeChange extends Revision {
  protected Entry myEntry;
  protected RootEntry myRoot;
  protected ChangeList myChangeList;
  protected Change myChange;

  public RevisionBeforeChange(Entry e, RootEntry r, ChangeList cl, Change c) {
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
    RootEntry copy = myRoot.copy();
    myChangeList.revertUpTo(copy, myChange, includeMyChange());
    return copy.getEntry(myEntry.getId());
  }

  @Override
  public List<Change> getSubsequentChanges() {
    List<Change> result = new ArrayList<Change>();
    result.addAll(myChangeList.getPlainChangesAfter(myChange));
    if (includeMyChange()) result.add(myChange);
    return result;
  }

  @Override
  public boolean isBefore(ChangeSet c) {
    return myChangeList.isBefore(myChange, c, includeMyChange());
  }

  protected boolean includeMyChange() {
    return true;
  }
}