package com.intellij.localvcs;

class CreateDirectoryChange implements Change {
  private String myName;

  public CreateDirectoryChange(String name) {
    myName = name;
  }

  public void applyTo(Snapshot snapshot) {
    snapshot.createDirectory(myName);
  }

  public void revertOn(Snapshot snapshot) {
    throw new UnsupportedOperationException();
  }
}
