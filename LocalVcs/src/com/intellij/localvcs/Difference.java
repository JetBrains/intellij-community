package com.intellij.localvcs;

import java.util.ArrayList;
import java.util.List;

// todo make it abstract
public class Difference {
  public enum Kind {
    NOT_CHANGED, CREATED, MODIFIED, DELETED
  }

  private boolean myIsFile;
  private Integer myId;
  private String myOldName;
  private String myNewName;
  private Kind myKind;

  private List<Difference> myChildren = new ArrayList<Difference>();

  public Difference(Integer id, String oldName, String newName) {
    myIsFile = false;
    myKind = Kind.NOT_CHANGED;
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
    myKind = k;
  }

  public void addChild(Difference d) {
    myChildren.add(d);
  }

  public List<Difference> getChildren() {
    return myChildren;
  }
}
