package com.intellij.localvcs;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ChangeList {
  // todo hold changes in reverse order
  private List<Change> myChanges = new ArrayList<Change>();

  public ChangeList() {
  }

  public ChangeList(Stream s) throws IOException {
    int count = s.readInteger();
    while (count-- > 0) {
      myChanges.add(s.readChange());
    }
  }

  public void write(Stream s) throws IOException {
    s.writeInteger(myChanges.size());
    for (Change c : myChanges) {
      s.writeChange(c);
    }
  }

  // todo test support
  public List<Change> getChanges() {
    List<Change> result = new ArrayList<Change>(myChanges);
    Collections.reverse(result);
    return result;
  }

  public List<Change> getChangesFor(RootEntry r, String path) {
    RootEntry rootCopy = r.copy();
    Entry e = rootCopy.getEntry(path);

    List<Change> result = new ArrayList<Change>();
    for (int i = myChanges.size() - 1; i >= 0; i--) {
      Change c = myChanges.get(i);
      if (c.affects(e)) result.add(c);
      if (c.isCreationalFor(e)) break;
      c.revertOn(rootCopy);
    }

    return result;
  }

  public void addChange(Change c) {
    myChanges.add(c);
  }

  public void revertUpTo(RootEntry r, Change target, boolean revertTargetChange) {
    for (int i = myChanges.size() - 1; i >= 0; i--) {
      Change c = myChanges.get(i);

      if (!revertTargetChange && c == target) return;
      c.revertOn(r);

      if (c == target) return;
    }
  }


  public List<Content> purgeUpTo(long timestamp) {
    List<Change> newChanges = new ArrayList<Change>();
    List<Content> purgedContents = new ArrayList<Content>();

    for (Change c : myChanges) {
      if (c.getTimestamp() < timestamp) {
        purgedContents.addAll(c.getContentsToPurge());
      }
      else {
        newChanges.add(c);
      }
    }
    myChanges = newChanges;

    return purgedContents;
  }
}
