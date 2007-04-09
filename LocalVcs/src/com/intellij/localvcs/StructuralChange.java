package com.intellij.localvcs;

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
    for (IdPath p : getAffectedIdPaths()) {
      if (p.contains(e.getId()) || e.getIdPath().contains(p.getId())) return true;
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
