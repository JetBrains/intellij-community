package com.intellij.localvcs;

public class RevisionAfterChange extends RevisionBeforeChange {
  public RevisionAfterChange(Entry e, RootEntry r, ChangeList cl, Change c) {
    super(e, r, cl, c);
  }

  @Override
  public String getCauseAction() {
    return myChange.getName();
  }

  @Override
  protected boolean revertMyChange() {
    return false;
  }
}
