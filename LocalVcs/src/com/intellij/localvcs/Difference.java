package com.intellij.localvcs;

public class Difference {

  public enum Kind {
    CREATED, MODIFIED, DELETED
  }

  private Kind myKind;

  public Difference(Kind k) {
    myKind = k;
  }

  public boolean isCreated() {
    return myKind.equals(Kind.CREATED);
  }

  public boolean isModified() {
    return myKind.equals(Kind.MODIFIED);
  }

  public boolean isDeleted() {
    return myKind.equals(Kind.DELETED);
  }
}
