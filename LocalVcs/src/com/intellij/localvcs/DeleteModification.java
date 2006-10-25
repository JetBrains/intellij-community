package com.intellij.localvcs;

class DeleteModification implements Modification {
  private String myName;
  private String myContent;

  public DeleteModification(String name) {
    myName = name;
  }

  public void applyTo(Snapshot snapshot) {
    myContent = snapshot.getFileRevision(myName).getContent();
    snapshot.deleteFile(myName);
  }

  public void revertOn(Snapshot snapshot) {
    snapshot.addFile(myName, myContent);
  }
}
