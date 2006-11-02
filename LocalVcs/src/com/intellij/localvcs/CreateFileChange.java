package com.intellij.localvcs;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class CreateFileChange extends Change {
  private Path myPath;
  private String myContent;

  public CreateFileChange(Path path, String content) {
    myPath = path;
    myContent = content;
  }

  public CreateFileChange(DataInputStream s) throws IOException {
    myPath = new Path(s);
    myContent = s.readUTF();
  }

  @Override
  public void write(DataOutputStream s) throws IOException {
    super.write(s);
    myPath.write(s);
    s.writeUTF(myContent);
  }

  @Override
  public void applyTo(Snapshot snapshot) {
    snapshot.doCreateFile(myPath, myContent);
  }

  @Override
  public void revertOn(Snapshot snapshot) {
    snapshot.doDelete(myPath);
  }

  @Override
  public String toString() {
    // todo generalize all toString and equals methods
    return getClass().getSimpleName() + "(" + myPath + ", " + myContent + ")";
  }

  @Override
  public boolean equals(Object o) {
    // todo generalize all toString and equals methods
    CreateFileChange c = (CreateFileChange)o;
    return myPath.equals(c.myPath) && myContent.equals(c.myContent);
  }

  @Override
  public int hashCode() {
    // todo generalize all toString and equals methods
    throw new UnsupportedOperationException();
  }
}
