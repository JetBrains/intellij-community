package com.intellij.localvcs;

import java.util.List;

public abstract class Revision {
  // todo rename to Entry
  private Integer myObjectId;
  private Revision myParent;
  private Path myName;

  public Revision(Integer objectId, Path name) {
    // todo replace Path name parameter with String name 
    myObjectId = objectId;
    myName = name;
  }

  public Integer getObjectId() {
    return myObjectId;
  }

  public Path getName() {
    return myName;
  }

  public Path getPath() {
    if (!hasParent()) return myName;
    return myParent.getPath().appendedWith(myName);
  }

  public String getContent() {
    throw new UnsupportedOperationException();
  }

  private boolean hasParent() {
    return myParent != null;
  }

  public Revision getParent() {
    return myParent;
  }

  protected void setParent(Revision parent) {
    myParent = parent;
  }

  public void addChild(Revision child) {
    throw new UnsupportedOperationException();
  }

  public void removeChild(Revision child) {
    throw new UnsupportedOperationException();
  }

  public List<Revision> getChildren() {
    throw new UnsupportedOperationException();
  }

  public Revision getRevision(Path path) {
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
    Revision r = (Revision)o;
    return myObjectId.equals(r.myObjectId) && myName.equals(r.myName);
  }

  @Override
  public int hashCode() {
    throw new UnsupportedOperationException();
  }
}
