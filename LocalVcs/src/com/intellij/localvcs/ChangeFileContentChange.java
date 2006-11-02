package com.intellij.localvcs;

public class ChangeFileContentChange extends Change {
  private Path myPath;
  private String myNewContent;
  private Entry myPreviousEntry;

  public ChangeFileContentChange(Path path, String newContent) {
    myPath = path;
    myNewContent = newContent;
  }

  @Override
  public void applyTo(Snapshot snapshot) {
    myPreviousEntry = snapshot.getEntry(myPath);
    snapshot.doChangeFileContent(myPath, myNewContent);
  }

  @Override
  public void revertOn(Snapshot snapshot) {
    snapshot.doChangeFileContent(myPath, myPreviousEntry.getContent());
  }
}
