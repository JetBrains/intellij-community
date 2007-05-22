package com.intellij.localvcs.core.changes;

import com.intellij.localvcs.core.IdPath;
import com.intellij.localvcs.core.storage.Content;
import com.intellij.localvcs.core.storage.Stream;
import com.intellij.localvcs.core.tree.Entry;
import com.intellij.localvcs.core.tree.RootEntry;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

public abstract class StructuralChange extends Change {
  protected String myPath; // transient
  protected IdPath myAffectedIdPath;

  protected StructuralChange(String path) {
    myPath = path;
  }

  protected StructuralChange(Stream s) throws IOException {
    myAffectedIdPath = s.readIdPath();
  }

  public void write(Stream s) throws IOException {
    s.writeIdPath(myAffectedIdPath);
  }

  @Override
  public void applyTo(RootEntry r) {
    myAffectedIdPath = doApplyTo(r);
    myPath = null;
  }

  protected abstract IdPath doApplyTo(RootEntry r);

  @Override
  public abstract void revertOn(RootEntry r);

  @Override
  public boolean affects(Entry e) {
    return affects(e.getIdPath());
  }

  private boolean affects(IdPath... pp) {
    for (IdPath p1 : getAffectedIdPaths()) {
      for (IdPath p2 : pp) {
        if (p1.isChildOrParentOf(p2)) return true;
      }
    }
    return false;
  }

  @Override
  public boolean affectsOnlyInside(Entry e) {
    for (IdPath p : getAffectedIdPaths()) {
      if (!p.startsWith(e.getIdPath())) {
        return false;
      }
    }
    return true;
  }

  @Override
  public boolean isInTheChain(List<Change> cc) {
    for (Change c : cc) {
      if (affects(((StructuralChange)c).getAffectedIdPaths())) {
        return true;
      }
    }
    return false;
  }

  @Override
  public boolean isCreationalFor(Entry e) {
    return false;
  }

  public IdPath[] getAffectedIdPaths() {
    return new IdPath[]{myAffectedIdPath};
  }

  @Override
  public List<Content> getContentsToPurge() {
    return Collections.emptyList();
  }
}
