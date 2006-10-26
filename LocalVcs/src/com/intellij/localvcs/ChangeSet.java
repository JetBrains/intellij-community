package com.intellij.localvcs;

import java.util.List;

public class ChangeSet {
  private String myLabel;
  private List<Change> myChanges;

  public ChangeSet(List<Change> changes) {
    myChanges = changes;
  }

  public String getLabel() {
    return myLabel;
  }

  public void setLabel(String label) {
    myLabel = label;
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
