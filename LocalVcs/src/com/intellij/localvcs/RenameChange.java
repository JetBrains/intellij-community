package com.intellij.localvcs;

class RenameChange implements Change {
  private Path myPath;
  private String myNewName;

  public RenameChange(Path path, String newName) {
    myPath = path;
    myNewName = newName;
  }

  public void applyTo(Snapshot snapshot) {
    snapshot.doRename(myPath, myNewName);
  }

  public void revertOn(Snapshot snapshot) {
    snapshot.doRename(myPath.renamedWith(myNewName), myPath.getName());
  }
}
