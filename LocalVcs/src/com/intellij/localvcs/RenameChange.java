package com.intellij.localvcs;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class RenameChange extends Change {
  private Path myPath;
  private String myNewName;

  public RenameChange(Path path, String newName) {
    myPath = path;
    myNewName = newName;
  }

  public RenameChange(DataInputStream s) throws IOException {
    myPath = new Path(s);
    myNewName = s.readUTF();
  }

  @Override
  public void write(DataOutputStream s) throws IOException {
    super.write(s);
    myPath.write(s);
    s.writeUTF(myNewName);
  }

  public Path getPath() {
    return myPath;
  }

  public String getNewName() {
    return myNewName;
  }

  @Override
  public void applyTo(Snapshot snapshot) {
    snapshot.doRename(myPath, myNewName);
  }

  @Override
  public void revertOn(Snapshot snapshot) {
    snapshot.doRename(myPath.renamedWith(myNewName), myPath.getName());
  }
}
