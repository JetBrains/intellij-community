package com.intellij.localvcs;

import java.util.ArrayList;
import java.util.List;

public class DirectoryRevision extends Revision {
  private List<Revision> myChildren = new ArrayList<Revision>();

  public DirectoryRevision(Integer objectId, Filename name) {
    super(objectId, name);
  }

  @Override
  public void addChild(Revision child) {
    myChildren.add(child);
    child.setParent(this);
  }

  @Override
  public void removeChild(Revision child) {
    for (int i = 0; i < myChildren.size(); i++) {
      if (myChildren.get(i) == child) {
        myChildren.remove(i);
        break;
      }
    }
    child.setParent(null);
  }

  @Override
  public List<Revision> getChildren() {
    return myChildren;
  }

  @Override
  public Revision getRevision(Filename path) {
    Revision result = super.getRevision(path);
    if (result != null) return result;

    for (Revision child : myChildren) {
      result = child.getRevision(path);
      if (result != null) return result;
    }

    return null;
  }
}
