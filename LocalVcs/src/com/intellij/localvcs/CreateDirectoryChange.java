package com.intellij.localvcs;

class CreateDirectoryChange implements Change {
  private FileName myPath;

  public CreateDirectoryChange(FileName path) {
    myPath = path;
  }

  public void applyTo(Snapshot snapshot) {
    snapshot.doCreateDirectory(myPath);
  }

  public void revertOn(Snapshot snapshot) {
    snapshot.doDelete(myPath);
  }
}
