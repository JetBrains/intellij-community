package com.intellij.localvcs;

import java.util.ArrayList;
import java.util.List;

public class DirectoryEntry extends Entry {
  private List<Entry> myChildren = new ArrayList<Entry>();

  public DirectoryEntry(Integer objectId, String name) {
    super(objectId, name);
  }

  protected Path getPathAppendedWith(String name) {
    return getPath().appendedWith(name);
  }

  @Override
  public Boolean isDirectory() {
    return true;
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
  public Entry findEntry(Matcher m) {
    // todo a bit messy
    Entry result = super.findEntry(m);
    if (result != null) return result;

    for (Entry child : myChildren) {
      result = child.findEntry(m);
      if (result != null) return result;
    }

    return null;
  }

  public Entry copy() {
    Entry result = copyEntry();
    for (Entry child : myChildren) {
      result.addChild(child.copy());
    }
    return result;
  }

  protected Entry copyEntry() {
    return new DirectoryEntry(myObjectId, myName);
  }
}
