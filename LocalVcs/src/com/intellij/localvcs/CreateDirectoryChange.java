package com.intellij.localvcs;

import java.io.IOException;

public class CreateDirectoryChange extends Change {
  private Path myPath;

  public CreateDirectoryChange(Path path) {
    myPath = path;
  }

  public CreateDirectoryChange(Stream s) throws IOException {
    myPath = s.readPath();
  }

  @Override
  public void write(Stream s) throws IOException {
    super.write(s);
    s.writePath(myPath);
  }

  public Path getPath() {
    return myPath;
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
