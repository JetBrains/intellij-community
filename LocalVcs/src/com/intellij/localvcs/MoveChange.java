package com.intellij.localvcs;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

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
  public void _revertOn(RootEntry root) {
    root.move(getNewPath(), Path.getParentOf(myPath));
  }

  private String getNewPath() {
    return Path.appended(myNewParentPath, Path.getNameOf(myPath));
  }
}
