package com.intellij.localvcs;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ChangeSet {
  private String myLabel;
  private long myTimestamp;
  private List<Change> myChanges;

  public ChangeSet(long timestamp, List<Change> changes) {
    myTimestamp = timestamp;
    myChanges = changes;
  }

  public ChangeSet(Stream s) throws IOException {
    myLabel = s.readString();
    myTimestamp = s.readLong();

    int count = s.readInteger();
    if (count > 0) {
      myChanges = new ArrayList<Change>(count);
      while (count-- > 0) {
        myChanges.add(s.readChange());
      }
    }
    else {
      myChanges = Collections.emptyList();
    }
  }

  public void write(Stream s) throws IOException {
    s.writeString(myLabel);
    s.writeLong(myTimestamp);

    s.writeInteger(myChanges.size());
    for (Change c : myChanges) {
      s.writeChange(c);
    }
  }

  public String getLabel() {
    return myLabel;
  }

  public long getTimestamp() {
    return myTimestamp;
  }

  public void setLabel(String label) {
    myLabel = label;
  }

  public List<Change> getChanges() {
    // todo this method is used only in tests 
    return myChanges;
  }

  public boolean hasChangesFor(Entry e) {
    for (Change c : myChanges) {
      if (c.affects(e)) return true;
    }
    return false;
  }

  public void applyTo(RootEntry root) {
    for (Change change : myChanges) {
      change.applyTo(root);
    }
  }

  public void revertOn(RootEntry e) {
    for (int i = myChanges.size() - 1; i >= 0; i--) {
      Change change = myChanges.get(i);
      change.revertOn(e);
    }
  }

  public List<Content> getContentsToPurge() {
    List<Content> result = new ArrayList<Content>();
    for (Change c : myChanges) {
      result.addAll(c.getContentsToPurge());
    }
    return result;
  }
}
