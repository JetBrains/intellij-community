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

  public Snapshot applyChangeSetOn(Snapshot snapshot, ChangeSet cs) {
    // todo we make bad assumption that applying is only done on current
    // todo snapshot - not on the reverted one

    // todo should we really make copy of current shapshot?
    // todo copy is a performance bottleneck
    Snapshot result = new Snapshot(snapshot);

    cs.applyTo(result);
    myChangeSets.add(cs);
    result.incrementIndex();

    return result;
  }

  public Snapshot revertLastChangeSetOn(Snapshot snapshot) {
    // todo 1. not as clear as i want it to be.
    // todo 2. throw an exception instead of returning null
    if (snapshot.getIndex() < 0) return null;

    Snapshot result = new Snapshot(snapshot);

    getChangeSetFor(result).revertOn(result);
    result.decrementIndex();

    return result;
  }

  private ChangeSet getChangeSetFor(Snapshot s) {
    // todo ummm... one more unpleasant check... 
    if (s.getIndex() < 0) throw new LocalVcsException();
    return myChangeSets.get(s.getIndex());
  }

  public void setLastChangeSetLabel(Snapshot s, String label) {
    getChangeSetFor(s).setLabel(label);
  }

  public String getLastChangeSetLabel(Snapshot s) {
    return getChangeSetFor(s).getLabel();
  }
}
