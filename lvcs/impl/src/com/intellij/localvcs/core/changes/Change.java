package com.intellij.localvcs.core.changes;

import com.intellij.localvcs.core.storage.Content;
import com.intellij.localvcs.core.storage.Stream;
import com.intellij.localvcs.core.tree.Entry;
import com.intellij.localvcs.core.tree.RootEntry;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

public abstract class Change {
  public abstract void write(Stream s) throws IOException;

  public String getName() {
    throw new UnsupportedOperationException();
  }

  public long getTimestamp() {
    throw new UnsupportedOperationException();
  }

  public List<Change> getChanges() {
    return Collections.singletonList(this);
  }

  public abstract void applyTo(RootEntry r);

  public abstract void revertOn(RootEntry r);

  public abstract boolean affects(Entry e);

  public abstract boolean affectsOnlyInside(Entry e);

  public void collectChangesInTheChain(List<Change> cc) {
    if (isInTheChain(cc)) cc.add(this);
  }

  public boolean isInTheChain(List<Change> cc) {
    return false;
  }

  public abstract boolean isCreationalFor(Entry e);

  public abstract List<Content> getContentsToPurge();

  public boolean isLabel() {
    return false;
  }

  public boolean isMark() {
    return false;
  }

  public void accept(ChangeVisitor v) throws IOException, ChangeVisitor.StopVisitingException {
  }
}
