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
    s.writePath(myPath);
  }

  public Path getPath() {
    return myPath;
  }

  @Override
  public void applyTo(RootEntry root) {
    root.doCreateDirectory(myPath, root.getNextObjectId());
  }

  @Override
  public void revertOn(RootEntry root) {
    root.doDelete(myPath);
  }
}
