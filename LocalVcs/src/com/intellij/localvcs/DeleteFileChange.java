package com.intellij.localvcs;

class DeleteFileChange implements Change {
  private FileName myName;
  private Revision myPreviousRevision;

  public DeleteFileChange(FileName name) {
    myName = name;
  }

  public void applyTo(Snapshot snapshot) {
    myPreviousRevision = snapshot.getRevision(myName);
    snapshot.doDelete(myName);
  }

  public void revertOn(Snapshot snapshot) {
    snapshot.doCreateFile(myName, myPreviousRevision.getContent());
  }
}
