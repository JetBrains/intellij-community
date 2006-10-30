package com.intellij.localvcs;

import java.util.ArrayList;
import java.util.List;

public class DirectoryEntry extends Entry {
  private List<Entry> myChildren = new ArrayList<Entry>();

  public DirectoryEntry(Integer objectId, String name) {
    super(objectId, name);
  }

  @Override
  public void addChild(Entry child) {
    myChildren.add(child);
    child.setParent(this);
  }

  @Override
  public void removeChild(Entry child) {
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
  public List<Entry> getChildren() {
    return myChildren;
  }

  @Override
  public Entry getEntry(Path path) {
    // todo a bit messy
    Entry result = super.getEntry(path);
    if (result != null) return result;

    for (Entry child : myChildren) {
      result = child.getEntry(path);
      if (result != null) return result;
    }

    return null;
  }
}
