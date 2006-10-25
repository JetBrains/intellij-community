package com.intellij.localvcs;

class AddFileChange implements Change {
  private String myName;
  private String myContent;

  public AddFileChange(String name, String content) {
    myName = name;
    myContent = content;
  }

  public void applyTo(Snapshot snapshot) {
    snapshot.addFile(myName, myContent);
  }

  public void revertOn(Snapshot snapshot) {
    snapshot.deleteFile(myName);
  }
}
