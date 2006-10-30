package com.intellij.localvcs;

import java.util.ArrayList;
import java.util.List;

public class DirectoryRevision extends Revision {
  private List<Revision> myChildren = new ArrayList<Revision>();

  public DirectoryRevision(Integer objectId, String name) {
    super(objectId, name);
  }

  @Override
  public void addChild(Revision child) {
    myChildren.add(child);
    child.setParent(this);
  }

  @Override
  public void removeChild(Revision child) {
    // todo should we remove child by equality or by identity?
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
  public Revision getRevision(Path path) {
    // todo a bit messy
    Revision result = super.getRevision(path);
    if (result != null) return result;

    for (Revision child : myChildren) {
      result = child.getRevision(path);
      if (result != null) return result;
    }

    return null;
  }
}
