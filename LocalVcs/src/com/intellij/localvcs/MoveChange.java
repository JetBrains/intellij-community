package com.intellij.localvcs;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

public class MoveChange extends Change {
  private String myPath;
  private String myNewParentPath;
  private IdPath myFromIdPath;
  private IdPath myToIdPath;

  public MoveChange(String path, String newParentPath) {
    myPath = path;
    myNewParentPath = newParentPath;
  }

  public MoveChange(Stream s) throws IOException {
    myPath = s.readString();
    myNewParentPath = s.readString();
  }

  @Override
  public void write(Stream s) throws IOException {
    s.writeString(myPath);
    s.writeString(myNewParentPath);
  }

  public String getPath() {
    return myPath;
  }

  public String getNewParentPath() {
    return myNewParentPath;
  }

  @Override
  public void applyTo(RootEntry root) {
    myFromIdPath = root.getEntry(myPath).getIdPath();
    root.move(myPath, myNewParentPath);
    myToIdPath = root.getEntry(getNewPath()).getIdPath();
  }

  @Override
  public void _revertOn(RootEntry root) {
    root.move(getNewPath(), Path.getParentOf(myPath));
  }

  private String getNewPath() {
    return Path.appended(myNewParentPath, Path.getNameOf(myPath));
  }

  @Override
  protected List<IdPath> getAffectedEntryIdPaths() {
    return Arrays.asList(myFromIdPath, myToIdPath);
  }
}
