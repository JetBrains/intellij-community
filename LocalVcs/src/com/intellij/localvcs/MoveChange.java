package com.intellij.localvcs;

import java.io.IOException;

public class MoveChange extends Change {
  private String myNewParentPath;

  public MoveChange(String path, String newParentPath) {
    super(path);
    myNewParentPath = newParentPath;
  }

  public MoveChange(Stream s) throws IOException {
    super(s);
    myNewParentPath = s.readString();
  }

  @Override
  public void write(Stream s) throws IOException {
    super.write(s);
    s.writeString(myNewParentPath);
  }

  public String getNewParentPath() {
    return myNewParentPath;
  }

  @Override
  public void applyTo(RootEntry root) {
    addAffectedIdPath(root.getEntry(myPath).getIdPath());
    root.move(myPath, myNewParentPath);
    addAffectedIdPath(root.getEntry(getNewPath()).getIdPath());
  }

  @Override
  public void revertOn(RootEntry root) {
    root.move(getNewPath(), Paths.getParentOf(myPath));
  }

  private String getNewPath() {
    return Paths.appended(myNewParentPath, Paths.getNameOf(myPath));
  }
}
