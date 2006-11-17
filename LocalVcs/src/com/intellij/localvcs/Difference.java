package com.intellij.localvcs;

import java.util.ArrayList;
import java.util.List;

public class Difference {
  public enum Kind {
    NOT_MODIFIED, CREATED, MODIFIED, DELETED
  }

  private boolean myIsFile;
  private Kind myKind;
  private Entry myLeft;
  private Entry myRight;

  private List<Difference> myChildren = new ArrayList<Difference>();

  public Difference(boolean isFile, Kind k, Entry left, Entry right) {
    myIsFile = isFile;
    myKind = k;
    myLeft = left;
    myRight = right;
  }

  public boolean isFile() {
    return myIsFile;
  }

  public Kind getKind() {
    return myKind;
  }

  public Entry getLeft() {
    return myLeft;
  }

  public Entry getRight() {
    return myRight;
  }

  public void addChild(Difference d) {
    myChildren.add(d);
  }

  public List<Difference> getChildren() {
    return myChildren;
  }

  public boolean hasDifference() {
    if (myKind.equals(Kind.MODIFIED)) return true;

    for (Difference child : myChildren) {
      if (child.hasDifference()) return true;
    }

    return false;
  }
}
