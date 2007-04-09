package com.intellij.localvcs;

public abstract class Revision {
  public String getName() {
    return null;
  }

  public abstract long getTimestamp();

  public String getCauseAction() {
    return null;
  }

  public abstract Entry getEntry();

  public Difference getDifferenceWith(Revision right) {
    Entry leftEntry = getEntry();
    Entry rightEntry = right.getEntry();

    return leftEntry.getDifferenceWith(rightEntry);
  }
}
