package com.intellij.localvcs;

import java.util.List;

public abstract class Revision {
  // todo rename to Entry
  private Integer myObjectId;
  private Filename myName;

  public Revision(Integer objectId, Filename name) {
    myObjectId = objectId;
    myName = name;
  }

  public Integer getObjectId() {
    return myObjectId;
  }

  public Filename getName() {
    return myName;
  }

  public String getContent() {
    throw new UnsupportedOperationException();
  }

  public List<Revision> getChildren() {
    throw new UnsupportedOperationException();
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
