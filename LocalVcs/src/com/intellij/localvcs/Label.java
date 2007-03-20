package com.intellij.localvcs;

public class Label {
  protected Entry myEntry;
  protected ChangeList myChangeList;
  protected ChangeSet myChangeSet;
  protected RootEntry myRoot;

  public Label(Entry e, RootEntry r, ChangeList cl, ChangeSet cs) {
    myEntry = e;
    myChangeList = cl;
    myChangeSet = cs;
    myRoot = r;
  }

  public String getName() {
    return myChangeSet.getLabel();
  }

  public long getTimestamp() {
    return myChangeSet.getTimestamp();
  }

  public Entry getEntry() {
    RootEntry copy = myRoot.copy();
    myChangeList.revertUpToChangeSet(copy, myChangeSet);
    return copy.getEntry(myEntry.getId());
  }

  public Difference getDifferenceWith(Label right) {
    // todo it seems that entries should always exist, but i'm not sure...
    // todo i cant figure out any test for it
    Entry leftEntry = getEntry();
    Entry rightEntry = right.getEntry();

    return leftEntry.getDifferenceWith(rightEntry);
  }
}
