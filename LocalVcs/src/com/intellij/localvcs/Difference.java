package com.intellij.localvcs;

import java.util.ArrayList;
import java.util.List;

// todo make it abstract
public class Difference {
  public enum Kind {
    NOT_MODIFIED, CREATED, MODIFIED, DELETED
  }

  private boolean myIsFile;
  private Integer myId;
  private String myOldName;
  private String myNewName;
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

  public Entry getLeft() {
    return myLeft;
  }

  public Entry getRight() {
    return myRight;
  }

  public Difference(Integer id, String oldName, String newName) {
    myIsFile = false;
    myKind = Kind.NOT_MODIFIED;
    myId = id;
    myOldName = oldName;
    myNewName = newName;
  }

  public Integer getId() {
    return myId;
  }

  public boolean isFile() {
    return myIsFile;
  }

  public void setIsFile(boolean b) {
    myIsFile = b;
  }

  public String getOldName() {
    return myOldName;
  }

  public void setOldName(String s) {
    myOldName = s;
  }

  public String getNewName() {
    return myNewName;
  }

  public void setNewName(String s) {
    myNewName = s;
  }

  public Kind getKind() {
    return myKind;
  }

  public void setKind(Kind k) {
    if (myKind.equals(Kind.NOT_MODIFIED)) myKind = k;
  }

  public void addChild(Difference d) {
    myChildren.add(d);
  }

  public Difference findChild(Integer id) {
    if (myId.equals(id)) return this;

    for (Difference d : myChildren) {
      Difference result = d.findChild(id);
      if (result != null) return result;
    }

    return null;
  }

  public List<Difference> getChildren() {
    return myChildren;
  }
}
