package com.intellij.localvcs;

class DeleteFileChange implements Change {
  private Path myName;
  private Revision myPreviousRevision;

  public DeleteFileChange(Path name) {
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
