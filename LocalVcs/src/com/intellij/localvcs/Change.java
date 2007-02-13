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

  public abstract void applyTo(RootEntry root);

  public abstract void revertOn(RootEntry root);

  public boolean affects(Entry e) {
    return myAffectedIdPath.contains(e.getId());
  }

  protected void setAffectedIdPath(IdPath p) {
    myAffectedIdPath = p;
  }

  protected IdPath getAffectedIdPath() {
    return myAffectedIdPath;
  }

  public List<Content> getContentsToPurge() {
    return Collections.emptyList();
  }
}
