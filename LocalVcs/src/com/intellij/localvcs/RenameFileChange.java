package com.intellij.localvcs;

class RenameFileChange implements Change {
  private Path myPath;
  private String myNewName;

  public RenameFileChange(Path path, String newName) {
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
