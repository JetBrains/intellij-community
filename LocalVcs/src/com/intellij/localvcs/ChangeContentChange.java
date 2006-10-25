package com.intellij.localvcs;

class ChangeContentChange implements Change {
  private String myName;
  private String myNewContent;
  private String myOldContent;

  public ChangeContentChange(String name, String newContent) {
    myName = name;
    myNewContent = newContent;
  }

  public void applyTo(Snapshot snapshot) {
    myOldContent = snapshot.getFileRevision(myName).getContent();
    snapshot.changeFile(myName, myNewContent);
  }

  public void revertOn(Snapshot snapshot) {
    snapshot.changeFile(myName, myOldContent);
  }
}
