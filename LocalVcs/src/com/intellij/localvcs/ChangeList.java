package com.intellij.localvcs;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ChangeList {
  private List<ChangeSet> myChangeSets = new ArrayList<ChangeSet>();

  public ChangeList() {
  }

  public ChangeList(Stream s) throws IOException {
    int count = s.readInteger();
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

  // todo test support
  public List<ChangeSet> getChangeSets() {
    List<ChangeSet> result = new ArrayList<ChangeSet>(myChangeSets);
    Collections.reverse(result);
    return result;
  }

  public List<ChangeSet> getChangeSetsFor(RootEntry r, String path) {
    RootEntry rootCopy = r.copy();
    Entry e = rootCopy.getEntry(path);

    List<ChangeSet> result = new ArrayList<ChangeSet>();
    for (int i = myChangeSets.size() - 1; i >= 0; i--) {
      ChangeSet cs = myChangeSets.get(i);
      if (cs.hasChangesFor(e)) result.add(cs);
      if (cs.isCreationalFor(e)) break;
      cs.revertOn(rootCopy);
    }

    return result;
  }

  public void addChangeSet(ChangeSet cs) {
    myChangeSets.add(cs);
  }

  public void revertUpToChangeSet(RootEntry r, ChangeSet cs) {
    for (int i = myChangeSets.size() - 1; i >= 0; i--) {
      ChangeSet changeSet = myChangeSets.get(i);
      changeSet.revertOn(r);
      if (changeSet == cs) return;
    }
  }

  public List<Content> purgeUpTo(long timestamp) {
    List<ChangeSet> newChangeSets = new ArrayList<ChangeSet>();
    List<Content> purgedContents = new ArrayList<Content>();

    for (ChangeSet cs : myChangeSets) {
      if (cs.getTimestamp() < timestamp) {
        purgedContents.addAll(cs.getContentsToPurge());
      }
      else {
        newChangeSets.add(cs);
      }
    }
    myChangeSets = newChangeSets;

    return purgedContents;
  }
}
