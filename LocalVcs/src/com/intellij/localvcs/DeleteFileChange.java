package com.intellij.localvcs;

class DeleteFileChange implements Change {
  private String myName;
  private String myContent;

  public DeleteFileChange(String name) {
    myName = name;
  }

  public void applyTo(Snapshot snapshot) {
    myContent = snapshot.getFileRevision(myName).getContent();
    snapshot.deleteFile(myName);
  }

  public void revertOn(Snapshot snapshot) {
    snapshot.createFile(myName, myContent);
  }
}
