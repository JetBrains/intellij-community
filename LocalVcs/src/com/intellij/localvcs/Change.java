package com.intellij.localvcs;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

public abstract class Change {
  protected String myPath;
  protected IdPath myAffectedIdPath;

  protected Change(String path) {
    myPath = path;
  }

  protected Change(Stream s) throws IOException {
    myAffectedIdPath = s.readIdPath();
  }

  public void write(Stream s) throws IOException {
    s.writeIdPath(myAffectedIdPath);
  }

  public void applyTo(RootEntry r) {
    myAffectedIdPath = doApplyTo(r);
    myPath = null;
  }

  protected abstract IdPath doApplyTo(RootEntry r);

  public abstract void revertOn(RootEntry r);

  public boolean affects(Entry e) {
    for (IdPath p : getAffectedIdPaths()) {
      if (p.contains(e.getId()) || e.getIdPath().contains(p.getName())) return true;
    }
    return false;
  }

  public boolean isCreationalFor(Entry e) {
    return false;
  }

  public IdPath[] getAffectedIdPaths() {
    return new IdPath[]{myAffectedIdPath};
  }

  public List<Content> getContentsToPurge() {
    return Collections.emptyList();
  }
}
