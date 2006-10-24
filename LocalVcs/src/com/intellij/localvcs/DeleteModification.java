package com.intellij.localvcs;

class DeleteModification implements Modification {
  private String myName;

  public DeleteModification(String name) {
    myName = name;
  }

  public void applyTo(Snapshot snapshot) {
    snapshot.doDeleteFile(myName);
  }
}
