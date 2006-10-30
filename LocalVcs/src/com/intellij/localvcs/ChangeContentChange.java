package com.intellij.localvcs;

class ChangeContentChange implements Change {
  private Path myPath;
  private String myNewContent;
  private Revision myPreviousRevision;

  public ChangeContentChange(Path path, String newContent) {
    myPath = path;
    myNewContent = newContent;
  }

  public void applyTo(Snapshot snapshot) {
    myPreviousRevision = snapshot.getRevision(myPath);
    snapshot.doChangeFile(myPath, myNewContent);
  }

  public void revertOn(Snapshot snapshot) {
    snapshot.doChangeFile(myPath, myPreviousRevision.getContent());
  }
}
