package com.intellij.localvcs;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class CreateDirectoryChange extends Change {
  private Path myPath;

  public CreateDirectoryChange(Path path) {
    myPath = path;
  }

  public CreateDirectoryChange(DataInputStream s) throws IOException {
    myPath = new Path(s);
  }

  @Override
  public void write(DataOutputStream s) throws IOException {
    super.write(s);
    myPath.write(s);
  }

  @Override
  public void applyTo(Snapshot snapshot) {
    snapshot.doCreateDirectory(myPath);
  }

  @Override
  public void revertOn(Snapshot snapshot) {
    snapshot.doDelete(myPath);
  }
}
