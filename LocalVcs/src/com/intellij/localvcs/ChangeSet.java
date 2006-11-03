package com.intellij.localvcs;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ChangeSet {
  private String myLabel;
  private List<Change> myChanges = new ArrayList<Change>();

  public ChangeSet(List<Change> changes) {
    myChanges = changes;
  }

  public ChangeSet(Stream s) throws IOException {
    myLabel = s.readString();

    Integer count = s.readInteger();
    for (int i = 0; i < count; i++) {
      myChanges.add(s.readChange());
    }
  }

  public void write(Stream s) throws IOException {
    s.writeString(myLabel);

    s.writeInteger(myChanges.size());
    for (Change c : myChanges) {
      s.writeChange(c);
    }
  }

  public String getLabel() {
    return myLabel;
  }

  public void setLabel(String label) {
    myLabel = label;
  }

  public List<Change> getChanges() {
    return myChanges;
  }

  public void applyTo(Snapshot snapshot) {
    for (Change change : myChanges) {
      change.applyTo(snapshot);
    }
  }

  public void revertOn(Snapshot snapshot) {
    for (int i = myChanges.size() - 1; i >= 0; i--) {
      Change change = myChanges.get(i);
      change.revertOn(snapshot);
    }
  }
}
