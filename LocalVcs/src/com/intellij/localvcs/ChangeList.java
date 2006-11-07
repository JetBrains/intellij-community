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
    // todo copy is a performance bottleneck
    // todo should we really make copy of current shapshot?

    // todo one more cast
    Snapshot result = new Snapshot((RootEntry)snapshot.getRoot().copy(), this);
    cs.applyTo(result);
    myChangeSets.add(cs);
    result.myIndex = myChangeSets.size() - 1;

    return result;
  }

  public Snapshot revertLastChangeSetOn(Snapshot snapshot) {
    // todo not as clear as i want it to be
    if (isEmpty()) return null; //todo throw exception

    // todo get rid of thisCopy
    ChangeList thisCopy = copy();
    Snapshot result =
        new Snapshot((RootEntry)snapshot.getRoot().copy(), thisCopy);

    thisCopy.getLast().revertOn(result);
    thisCopy.myChangeSets.remove(thisCopy.getLast());

    result.myIndex = thisCopy.myChangeSets.size() - 1;

    return result;
  }

  public Boolean isEmpty() {
    return myChangeSets.isEmpty();
  }

  private ChangeSet getLast() {
    // todo ummm... one more unpleasant check... 
    if (isEmpty()) throw new LocalVcsException();
    return myChangeSets.get(myChangeSets.size() - 1);
  }

  public void setLastChangeSetLabel(String label) {
    getLast().setLabel(label);
  }

  public String getLastChangeSetLabel() {
    return getLast().getLabel();
  }

  public ChangeList copy() {
    ChangeList result = new ChangeList();
    result.myChangeSets.addAll(myChangeSets);
    return result;
  }
}
