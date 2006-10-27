package com.intellij.localvcs;

class RenameFileChange implements Change {
  private Filename myName;
  private Filename myNewName;

  public RenameFileChange(Filename name, Filename newName) {
    myName = name;
    myNewName = newName;
  }

  public void applyTo(Snapshot snapshot) {
    snapshot.renameFile(myName, myNewName);
  }

  public void revertOn(Snapshot snapshot) {
    snapshot.renameFile(myNewName, myName);
  }
}
