package com.intellij.localvcs;

class CreateFileChange implements Change {
  private String myName;
  private String myContent;

  public CreateFileChange(String name, String content) {
    myName = name;
    myContent = content;
  }

  public void applyTo(Snapshot snapshot) {
    if (snapshot.hasFile(myName)) {
      throw new LocalVcsException();
    }
    snapshot.createFile(myName, myContent);
  }

  public void revertOn(Snapshot snapshot) {
    snapshot.deleteFile(myName);
  }
}
