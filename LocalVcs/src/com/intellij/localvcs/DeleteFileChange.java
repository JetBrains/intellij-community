package com.intellij.localvcs;

class DeleteFileChange implements Change {
  private Path myPath;
  private Revision myPreviousRevision;

  public DeleteFileChange(Path path) {
    myPath = path;
  }

  public void applyTo(Snapshot snapshot) {
    myPreviousRevision = snapshot.getRevision(myPath);
    snapshot.doDelete(myPath);
  }

  public void revertOn(Snapshot snapshot) {
    snapshot.doCreateFile(myPath, myPreviousRevision.getContent());
  }
}
