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

  public void revertEntryUpToChangeSet(Entry e, ChangeSet cs) {
    for (int i = myChangeSets.size() - 1; i >= 0; i--) {
      ChangeSet changeSet = myChangeSets.get(i);
      if (changeSet == cs) return;
      changeSet.revertOn(e);
    }
  }

  public void labelLastChangeSet(String label) {
    // todo try to remove this check 
    assert !myChangeSets.isEmpty();

    ChangeSet last = myChangeSets.get(myChangeSets.size() - 1);
    last.setLabel(label);
  }
}
