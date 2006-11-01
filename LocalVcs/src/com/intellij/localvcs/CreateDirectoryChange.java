package com.intellij.localvcs;

public class CreateDirectoryChange implements Change {
  private Path myPath;

  public CreateDirectoryChange(Path path) {
    myPath = path;
  }

  public void applyTo(Snapshot snapshot) {
    snapshot.doCreateDirectory(myPath);
  }

  public void revertOn(Snapshot snapshot) {
    snapshot.doDelete(myPath);
  }
}
