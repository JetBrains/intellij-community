package com.intellij.localvcs;

class RenameFileChange implements Change {
  private FileName myName;
  private FileName myNewName;

  public RenameFileChange(FileName name, FileName newName) {
    myName = name;
    myNewName = newName;
  }

  public void applyTo(Snapshot snapshot) {
    snapshot.doRename(myName, myNewName);
  }

  public void revertOn(Snapshot snapshot) {
    snapshot.doRename(myNewName, myName);
  }
}
