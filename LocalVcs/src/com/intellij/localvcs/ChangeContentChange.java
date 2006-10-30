package com.intellij.localvcs;

class ChangeContentChange implements Change {
  private FileName myName;
  private String myNewContent;
  private Revision myPreviousRevision;

  public ChangeContentChange(FileName name, String newContent) {
    myName = name;
    myNewContent = newContent;
  }

  public void applyTo(Snapshot snapshot) {
    myPreviousRevision = snapshot.getRevision(myName);
    snapshot.doChangeFile(myName, myNewContent);
  }

  public void revertOn(Snapshot snapshot) {
    snapshot.doChangeFile(myName, myPreviousRevision.getContent());
  }
}
