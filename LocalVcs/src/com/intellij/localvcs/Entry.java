package com.intellij.localvcs;

import java.util.List;

public abstract class Entry {
  // todo rename to Entry
  private Integer myObjectId;
  private Entry myParent;
  private String myName;

  public Entry(Integer objectId, String name) {
    myObjectId = objectId;
    myName = name;
  }

  public Integer getObjectId() {
    return myObjectId;
  }

  public Path getPath() {
    if (!hasParent()) return new Path(myName);
    return myParent.getPath().appendedWith(myName);
  }

  public String getContent() {
    throw new UnsupportedOperationException();
  }

  private boolean hasParent() {
    return myParent != null;
  }

  public Entry getParent() {
    return myParent;
  }

  protected void setParent(Entry parent) {
    myParent = parent;
  }

  public void addChild(Entry child) {
    throw new UnsupportedOperationException();
  }

  public void removeChild(Entry child) {
    throw new UnsupportedOperationException();
  }

  public List<Entry> getChildren() {
    throw new UnsupportedOperationException();
  }

  public Entry getRevision(Path path) {
    if (path.equals(getPath())) return this;
    return null;
  }

  @Override
  public String toString() {
    return getClass().getSimpleName()
           + "(id: " + myObjectId + ", "
           + "name: " + myName + ")";
  }

  @Override
  public boolean equals(Object o) {
    if (o == null || !o.getClass().equals(getClass())) return false;
    Entry r = (Entry)o;
    return myObjectId.equals(r.myObjectId) && myName.equals(r.myName);
  }

  @Override
  public int hashCode() {
    throw new UnsupportedOperationException();
  }
}
