package com.intellij.localvcs;

import java.util.ArrayList;
import java.util.List;

public class DirectoryRevision extends Revision {
  private List<Revision> myChildren = new ArrayList<Revision>();

  public DirectoryRevision(Integer objectId, Filename name) {
    super(objectId, name);
  }

  @Override
  public List<Revision> getChildren() {
    return myChildren;
  }
}
