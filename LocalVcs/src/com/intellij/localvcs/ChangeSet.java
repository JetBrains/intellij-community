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

  public void applyTo(Snapshot target) {
    for (Change change : myChanges) {
      change.applyTo(target);
    }
  }

  public void revertOn(Snapshot target) {
    for (Change change : myChanges) {
      change.revertOn(target);
    }
  }
}
