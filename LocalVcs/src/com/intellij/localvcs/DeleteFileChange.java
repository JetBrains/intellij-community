package com.intellij.localvcs;

class DeleteFileChange implements Change {
  private Path myPath;
  private Entry myPreviousEntry;

  public DeleteFileChange(Path path) {
    myPath = path;
  }

  public void applyTo(Snapshot snapshot) {
    myPreviousEntry = snapshot.getEntry(myPath);
    snapshot.doDelete(myPath);
  }

  public void revertOn(Snapshot snapshot) {
    snapshot.doCreateFile(myPath, myPreviousEntry.getContent());
  }
}
