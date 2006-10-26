package com.intellij.localvcs;

class DeleteFileChange implements Change {
  private String myName;
  private Revision myPreviousRevision;

  public DeleteFileChange(String name) {
    myName = name;
  }

  public void applyTo(Snapshot snapshot) {
    myPreviousRevision = snapshot.getFileRevision(myName);
    snapshot.deleteFile(myName);
  }

  public void revertOn(Snapshot snapshot) {
    snapshot.createFile(myName, myPreviousRevision.getContent());
  }
}
