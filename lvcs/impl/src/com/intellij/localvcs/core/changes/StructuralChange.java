package com.intellij.localvcs.core.changes;

import com.intellij.localvcs.core.IdPath;
import com.intellij.localvcs.core.storage.Content;
import com.intellij.localvcs.core.storage.Stream;
import com.intellij.localvcs.core.tree.Entry;

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
  public void applyTo(Entry r) {
    myAffectedIdPath = doApplyTo(r);
    myPath = null;
  }

  protected abstract IdPath doApplyTo(Entry r);

  @Override
  public abstract void revertOn(Entry r);

  @Override
  protected boolean affects(IdPath... pp) {
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
  public boolean affectsSameAs(List<Change> cc) {
    for (Change c : cc) {
      if (c.affects(getAffectedIdPaths())) return true;
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
