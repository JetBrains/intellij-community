package com.intellij.localvcs;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class ChangeFileContentChange extends Change {
  private Path myPath;
  private String myNewContent;
  private String myOldContent;

  public ChangeFileContentChange(Path path, String newContent) {
    myPath = path;
    myNewContent = newContent;
  }

  public ChangeFileContentChange(DataInputStream s) throws IOException {
    myPath = new Path(s);
    myNewContent = s.readUTF();
    if (s.readBoolean()) {
      myOldContent = s.readUTF();
    }
  }

  @Override
  public void write(DataOutputStream s) throws IOException {
    super.write(s);
    myPath.write(s);
    s.writeUTF(myNewContent);
    if (myOldContent != null) {
      s.writeBoolean(true);
      s.writeUTF(myOldContent);
    } else {
      s.writeBoolean(false);
    }
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
