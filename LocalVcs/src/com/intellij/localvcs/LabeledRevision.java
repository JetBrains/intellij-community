package com.intellij.localvcs;

public class LabeledRevision extends RevisionBeforeChange {
  public LabeledRevision(Entry e, RootEntry r, ChangeList cl, Change c) {
    super(e, r, cl, c);
  }

  @Override
  public String getName() {
    return myChange.getName();
  }
}