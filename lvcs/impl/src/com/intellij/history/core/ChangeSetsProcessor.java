package com.intellij.history.core;

import com.intellij.history.core.changes.Change;
import com.intellij.history.core.tree.Entry;

import java.util.List;

public abstract class ChangeSetsProcessor {
  protected LocalVcs myVcs;
  protected String myPath;
  protected Entry myEntry;

  public ChangeSetsProcessor(LocalVcs vcs, String path) {
    myVcs = vcs;
    myPath = path;
    myEntry = myVcs.getEntry(path);
  }

  protected void process() {
    List<Change> changes = collectChanges();

    if (changes.isEmpty()) {
      nothingToVisit(myEntry.getTimestamp());
      return;
    }

    for (Change c : changes) {
      if (c.isLabel()) {
        visitLabel(c);
      }
      else {
        visitRegular(c);
      }
    }

    Change lastChange = changes.get(changes.size() - 1);
    if (!lastChange.isLabel() && !lastChange.isCreationalFor(myEntry)) {
      visitFirstAvailableNonCreational(lastChange);
    }
  }

  protected abstract List<Change> collectChanges();

  protected abstract void nothingToVisit(long timestamp);

  protected abstract void visitLabel(Change c);

  protected abstract void visitRegular(Change c);

  protected abstract void visitFirstAvailableNonCreational(Change c);
}