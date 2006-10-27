package com.intellij.localvcs;

class CreateDirectoryChange implements Change {
  private Filename myName;

  public CreateDirectoryChange(Filename name) {
    myName = name;
  }

  public void applyTo(Snapshot snapshot) {
    snapshot.doCreateDirectory(myName);
  }

  public void revertOn(Snapshot snapshot) {
    throw new UnsupportedOperationException();
  }
}
