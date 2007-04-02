package com.intellij.localvcs;

public class Label {
  protected Entry myEntry;
  protected RootEntry myRoot;
  protected ChangeList myChangeList;
  protected ChangeSet myChangeSet;
  private String myName;
  private long myTimestamp;

  public Label(Entry e, RootEntry r, ChangeList cl, ChangeSet cs, String name, long timestamp) {
    myEntry = e;
    myRoot = r;
    myChangeList = cl;
    myChangeSet = cs;
    myName = name;
    myTimestamp = timestamp;
  }

  public String getName() {
    return myName;
  }

  public long getTimestamp() {
    return myTimestamp;
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
