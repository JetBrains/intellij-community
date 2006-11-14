package com.intellij.localvcs;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ChangeList {
  private List<ChangeSet> myChangeSets = new ArrayList<ChangeSet>();

  public ChangeList() { }

  public ChangeList(Stream s) throws IOException {
    Integer count = s.readInteger();
    while (count-- > 0) {
      myChangeSets.add(s.readChangeSet());
    }
  }

  public void write(Stream s) throws IOException {
    s.writeInteger(myChangeSets.size());
    for (ChangeSet c : myChangeSets) {
      s.writeChangeSet(c);
    }
  }

  public List<ChangeSet> getChangeSets() {
    return myChangeSets;
  }

  public void applyChangeSetOn(RootEntry root, ChangeSet cs) {
    // todo we make bad assumption that applying is only done on current
    // todo snapshot - not on the reverted one

    // todo should we really make copy of current shapshot?
    // todo copy is a performance bottleneck

    root.apply(cs);
    myChangeSets.add(cs);
    root.incrementChangeListIndex(); // todo do something with it
  }

  public RootEntry revertOn(RootEntry root) {
    // todo 1. not as clear as i want it to be.
    if (!root.canBeReverted()) throw new LocalVcsException();

    ChangeSet cs = getChangeSetFor(root);

    RootEntry result = root.revert(cs);
    result.decrementChangeListIndex();

    return result;
  }

  private ChangeSet getChangeSetFor(RootEntry root) {
    // todo ummm... one more unpleasant check...

    // todo VERY BAD!!! something wring with changeListIndex!!
    if (!root.canBeReverted()) throw new LocalVcsException();
    return myChangeSets.get(root.getChangeListIndex());
  }

  public DifferenceList getDifferenceListFor(RootEntry r, Entry e) {
    return new DifferenceList(this, r, e);
  }

  public void labelLastChangeSet(String label) {
    if (myChangeSets.isEmpty()) throw new LocalVcsException();

    ChangeSet last = myChangeSets.get(myChangeSets.size() - 1);
    last.setLabel(label);
  }
}
