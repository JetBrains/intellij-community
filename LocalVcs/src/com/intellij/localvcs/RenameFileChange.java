package com.intellij.localvcs;

class RenameFileChange implements Change {
  private String myName;
  private String myNewName;

  public RenameFileChange(String name, String newName) {
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
