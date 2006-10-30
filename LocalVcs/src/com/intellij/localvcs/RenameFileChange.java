package com.intellij.localvcs;

class RenameFileChange implements Change {
  private Path myName;
  private Path myNewName;

  public RenameFileChange(Path name, Path newName) {
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
