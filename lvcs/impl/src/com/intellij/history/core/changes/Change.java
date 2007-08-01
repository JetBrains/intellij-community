package com.intellij.history.core.changes;

import com.intellij.history.core.IdPath;
import com.intellij.history.core.storage.Content;
import com.intellij.history.core.storage.Stream;
import com.intellij.history.core.tree.Entry;

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

  public abstract void applyTo(Entry r);

  public abstract void revertOn(Entry r);

  public boolean canRevertOn(Entry r) {
    return true;
  }

  public boolean affects(Entry e) {
    return affects(e.getIdPath());
  }

  protected abstract boolean affects(IdPath... pp);

  public boolean affectsSameAs(List<Change> cc) {
    return false;
  }

  public abstract boolean affectsOnlyInside(Entry e);

  public abstract boolean isCreationalFor(Entry e);

  public abstract List<Content> getContentsToPurge();

  public boolean isLabel() {
    return false;
  }

  public boolean isSystemLabel() {
    return false;
  }

  public boolean isFileContentChange() {
    return false;
  }

  public void accept(ChangeVisitor v) throws IOException, ChangeVisitor.StopVisitingException {
  }
}
