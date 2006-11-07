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

  public void add(ChangeSet cs) {
    myChangeSets.add(cs);
  }

  public void revertLastChangeSetOn(Snapshot snapshot) {
    getLast().revertOn(snapshot);
    myChangeSets.remove(getLast());
  }

  public Boolean isEmpty() {
    return myChangeSets.isEmpty();
  }

  public ChangeSet getLast() {
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
