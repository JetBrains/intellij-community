package com.intellij.localvcs;

class DeleteFileChange implements Change {
  private Filename myName;
  private Revision myPreviousRevision;

  public DeleteFileChange(Filename name) {
    myName = name;
  }

  public void applyTo(Snapshot snapshot) {
    myPreviousRevision = snapshot.getRevision(myName);
    snapshot.doDeleteFile(myName);
  }

  public void revertOn(Snapshot snapshot) {
    snapshot.doCreateFile(myName, myPreviousRevision.getContent());
  }
}
