package com.intellij.localvcs;

import java.io.IOException;

public class ChangeFileContentChange extends Change {
  private Path myPath;
  private String myNewContent;
  private String myOldContent;
  private Integer myAffectedEntryId;

  public ChangeFileContentChange(Path path, String newContent) {
    myPath = path;
    myNewContent = newContent;
  }

  public ChangeFileContentChange(Stream s) throws IOException {
    myPath = s.readPath();
    myNewContent = s.readString();
    myOldContent = s.readString();
  }

  @Override
  public void write(Stream s) throws IOException {
    s.writePath(myPath);
    s.writeString(myNewContent);
    s.writeString(myOldContent);
  }

  public Path getPath() {
    return myPath;
  }

  public String getNewContent() {
    return myNewContent;
  }

  public String getOldContent() {
    return myOldContent;
  }

  @Override
  public void applyTo(RootEntry root) {
    Entry affectedEntry = root.getEntry(myPath);

    myOldContent = affectedEntry.getContent();
    myAffectedEntryId = affectedEntry.getObjectId();

    root.doChangeFileContent(myPath, myNewContent);
  }

  @Override
  public void revertOn(RootEntry root) {
    root.doChangeFileContent(myPath, myOldContent);
  }

  @Override
  public Integer getAffectedEntryId() {
    return myAffectedEntryId;
  }
}
