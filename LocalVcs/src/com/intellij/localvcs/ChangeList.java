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

  public ChangeList getChangeListFor(Entry e) {
    List<ChangeSet> sets = new ArrayList<ChangeSet>();

    for (ChangeSet cs : myChangeSets) {
      if (cs.hasChangesFor(e)) sets.add(cs);
    }

    ChangeList cl = new ChangeList();
    cl.myChangeSets = sets;
    return cl;
  }

  public void applyChangeSetTo(RootEntry root, ChangeSet cs) {
    cs.applyTo(root);
    myChangeSets.add(cs);
  }

  public void _revertUpToChangeSetOn(RootEntry root, ChangeSet cs) {
    for (int i = myChangeSets.size() - 1; i >= 0; i--) {
      ChangeSet changeSet = myChangeSets.get(i);
      if (changeSet == cs) return;
      changeSet._revertOn(root);
    }
  }

  public Entry revertEntryUpToChangeSet(Entry e, ChangeSet cs) {
    for (int i = myChangeSets.size() - 1; i >= 0; i--) {
      ChangeSet changeSet = myChangeSets.get(i);
      if (changeSet == cs) return e;
      e = changeSet.revertOn(e);
    }
    return e;
  }

  public void labelLastChangeSet(String label) {
    // todo try to remove this check 
    if (myChangeSets.isEmpty()) throw new LocalVcsException();

    ChangeSet last = myChangeSets.get(myChangeSets.size() - 1);
    last.setLabel(label);
  }

  //
  // candidates to remove
  //

  public void applyChangeSetOn_old(RootEntry root, ChangeSet cs) {
    // todo we make bad assumption that applying is only done on current
    // todo snapshot - not on the reverted one

    // todo should we really make copy of current shapshot?
    // todo copy is a performance bottleneck

    root.apply_old(cs);
    myChangeSets.add(cs);
    root.incrementChangeListIndex(); // todo do something with it
  }

  public RootEntry revertOn_old(RootEntry root) {
    // todo 1. not as clear as i want it to be.
    if (!root.canBeReverted()) throw new LocalVcsException();

    ChangeSet cs = getChangeSetFor_old(root);

    RootEntry result = root.revert_old(cs);
    result.decrementChangeListIndex();

    return result;
  }

  private ChangeSet getChangeSetFor_old(RootEntry root) {
    // todo ummm... one more unpleasant check...

    // todo VERY BAD!!! something wring with changeListIndex!!
    if (!root.canBeReverted()) throw new LocalVcsException();
    return myChangeSets.get(root.getChangeListIndex());
  }
}
