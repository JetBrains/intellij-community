package com.intellij.localvcs;

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
    myChangeList.revertUpTo(copy, myChange, revertMyChange());
    return copy.getEntry(myEntry.getId());
  }

  protected boolean revertMyChange() {
    return true;
  }
}