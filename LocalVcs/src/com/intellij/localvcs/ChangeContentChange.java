package com.intellij.localvcs;

class ChangeContentChange implements Change {
  private Filename myName;
  private String myNewContent;
  private Revision myPreviousRevision;

  public ChangeContentChange(Filename name, String newContent) {
    myName = name;
    myNewContent = newContent;
  }

  public void applyTo(Snapshot snapshot) {
    myPreviousRevision = snapshot.getRevision(myName);
    snapshot.changeFile(myName, myNewContent);
  }

  public void revertOn(Snapshot snapshot) {
    snapshot.changeFile(myName, myPreviousRevision.getContent());
  }
}
