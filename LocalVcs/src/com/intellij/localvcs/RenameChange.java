package com.intellij.localvcs;

public class RenameChange extends Change {
  private Path myPath;
  private String myNewName;

  public RenameChange(Path path, String newName) {
    myPath = path;
    myNewName = newName;
  }

  @Override
  public void applyTo(Snapshot snapshot) {
    snapshot.doRename(myPath, myNewName);
  }

  @Override
  public void revertOn(Snapshot snapshot) {
    snapshot.doRename(myPath.renamedWith(myNewName), myPath.getName());
  }
}
