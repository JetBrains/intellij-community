package com.intellij.history.core;

import com.intellij.history.core.changes.Change;
import com.intellij.history.core.changes.ChangeList;
import com.intellij.history.core.revisions.*;
import com.intellij.history.core.tree.Entry;

import java.util.ArrayList;
import java.util.List;

public class RevisionsCollector extends ChangeSetsProcessor {
  private Entry myRoot;
  private ChangeList myChangeList;

  private List<Revision> myResult = new ArrayList<Revision>();

  public RevisionsCollector(LocalVcs vcs, String path, Entry rootEntry, ChangeList cl) {
    super(vcs, path);

    myRoot = rootEntry;
    myChangeList = cl;
  }

  public List<Revision> getResult() {
    process();
    return myResult;
  }

  @Override
  protected List<Change> collectChanges() {
    return myChangeList.getChangesFor(myRoot, myPath);
  }

  @Override
  protected void notingToVisit(long timestamp) {
    myResult.add(new CurrentRevision(myEntry, timestamp));
  }

  @Override
  protected void visitLabel(Change c) {
    myResult.add(new LabeledRevision(myEntry, myRoot, myChangeList, c));
  }

  @Override
  protected void visitRegular(Change c) {
    myResult.add(new RevisionAfterChange(myEntry, myRoot, myChangeList, c));
  }

  @Override
  protected void visitFirstAvailableNonCreational(Change c) {
    myResult.add(new RevisionBeforeChange(myEntry, myRoot, myChangeList, c));
  }
}
