package com.intellij.localvcs;

public class Difference {
  public enum Kind {
    NULL, MODIFIED
  }

  private Kind myKind;

  public Difference(Kind kind) {
    myKind = kind;
  }

  public boolean isModified() {
    return isOfKind(Kind.MODIFIED);
  }

  public boolean isNull() {
    return isOfKind(Kind.NULL);
  }

  private boolean isOfKind(Kind k) {
    return myKind.equals(k);
  }
}
