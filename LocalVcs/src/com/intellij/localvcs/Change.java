package com.intellij.localvcs;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public abstract class Change {
  private List<IdPath> myAffectedIdPaths = new ArrayList<IdPath>();
  protected String myPath;

  protected Change(String path) {
    myPath = path;
  }

  protected Change(Stream s) throws IOException {
    myPath = s.readString();
    int count = s.readInteger();
    while (count-- > 0) {
      addAffectedIdPath(s.readIdPath());
    }
  }

  public void write(Stream s) throws IOException {
    s.writeString(myPath);
    s.writeInteger(myAffectedIdPaths.size());
    for (IdPath p : myAffectedIdPaths) {
      s.writeIdPath(p);
    }
  }

  public String getPath() {
    return myPath;
  }

  public abstract void applyTo(RootEntry root);

  public abstract void _revertOn(RootEntry root);

  public void revertFile(Entry e) { }

  public void revertOn(Entry e) {
    // todo replace with polymorphims
    // todo clean up revertion stuffs
    if (e instanceof FileEntry) {
      revertFile(e);
    }
    else {
      throw new RuntimeException("under construction");
    }
  }

  public boolean affects(Entry e) {
    // todo test it
    for (IdPath p : myAffectedIdPaths) {
      if (p.contains(e.getId())) return true;
    }
    return false;
  }

  protected boolean isFor(Entry e) {
    // todo test it
    for (IdPath p : myAffectedIdPaths) {
      if (p.getName().equals(e.getId())) return true;
    }
    return false;
  }

  protected void addAffectedIdPath(IdPath p) {
    myAffectedIdPaths.add(p);
  }

  // todo try to remove it
  protected List<IdPath> getAffectedIdPaths() {
    return myAffectedIdPaths;
  }
}
