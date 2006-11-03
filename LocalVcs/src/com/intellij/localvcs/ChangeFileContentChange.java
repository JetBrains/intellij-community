package com.intellij.localvcs;

import java.io.IOException;

public class ChangeFileContentChange extends Change {
  private Path myPath;
  private String myNewContent;
  private String myOldContent;

  public ChangeFileContentChange(Path path, String newContent) {
    myPath = path;
    myNewContent = newContent;
  }

  public ChangeFileContentChange(Stream s) throws IOException {
    myPath = s.readPath();
    myNewContent = s.readString();
    myOldContent = s.readNullableString();
  }

  @Override
  public void write(Stream s) throws IOException {
    super.write(s);
    s.writePath(myPath);
    s.writeString(myNewContent);
    s.writeNullableString(myOldContent);
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
  public void applyTo(Snapshot snapshot) {
    myOldContent = snapshot.getEntry(myPath).getContent();
    snapshot.doChangeFileContent(myPath, myNewContent);
  }

  @Override
  public void revertOn(Snapshot snapshot) {
    snapshot.doChangeFileContent(myPath, myOldContent);
  }
}
