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

  public abstract void revertOn(RootEntry root);

  public boolean affects(Entry e) {
    // todo test it
    for (IdPath p : myAffectedIdPaths) {
      if (p.contains(e.getId())) return true;
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
