package com.intellij.localvcs;

public class CreateFileChange implements Change {
  private Path myPath;
  private String myContent;

  public CreateFileChange(Path path, String content) {
    myPath = path;
    myContent = content;
  }

  public void applyTo(Snapshot snapshot) {
    snapshot.doCreateFile(myPath, myContent);
  }

  public void revertOn(Snapshot snapshot) {
    snapshot.doDelete(myPath);
  }
}
