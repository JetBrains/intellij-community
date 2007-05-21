package com.intellij.localvcs.core.revisions;

import com.intellij.localvcs.core.changes.Change;

public class RecentChange {
  private Revision myBefore;
  private Revision myAfter;

  public RecentChange(Revision before, Revision after) {
    myBefore = before;
    myAfter = after;
  }

  public Revision getRevisionBefore() {
    return myBefore;
  }

  public Revision getRevisionAfter() {
    return myAfter;
  }

  public Change getChange() {
    return myAfter.getCauseChange();
  }

  public String getChangeName() {
    return getChange().getName();
  }
}
