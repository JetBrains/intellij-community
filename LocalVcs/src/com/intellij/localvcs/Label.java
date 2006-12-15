package com.intellij.localvcs;

public class Label {
  private Entry myEntry;
  private ChangeList myChangeList;
  private ChangeSet myChangeSet;
  private RootEntry myRoot;

  public Label(Entry e, ChangeList cl, ChangeSet cs, RootEntry r) {
    myEntry = e;
    myChangeList = cl;
    myChangeSet = cs;
    myRoot = r;
  }

  public String getName() {
    return myChangeSet.getLabel();
  }

  public Entry getEntry() {
    // todo cleanup this mess
    if (myEntry instanceof FileEntry) {
      Entry e = myEntry.copy();
      myChangeList.revertEntryUpToChangeSet(e, myChangeSet);
      return e;
    }
    RootEntry copy = myRoot.copy();
    myChangeList._revertUpToChangeSetOn(copy, myChangeSet);

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
