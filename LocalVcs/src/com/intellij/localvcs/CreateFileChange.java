package com.intellij.localvcs;

class CreateFileChange implements Change {
  private Path myName;
  private String myContent;

  public CreateFileChange(Path name, String content) {
    myName = name;
    myContent = content;
  }

  public void applyTo(Snapshot snapshot) {
    snapshot.doCreateFile(myName, myContent);
  }

  public void revertOn(Snapshot snapshot) {
    snapshot.doDelete(myName);
  }
}
