package com.intellij.localvcs;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class MoveChange extends Change {
  private Path myPath;
  private Path myNewParent;
  private List<IdPath> myAffectedEntryIdPaths = new ArrayList<IdPath>();

  public MoveChange(Path path, Path newParent) {
    myPath = path;
    myNewParent = newParent;
  }

  public MoveChange(Stream s) throws IOException {
    myPath = s.readPath();
    myNewParent = s.readPath();
  }

  @Override
  public void write(Stream s) throws IOException {
    s.writePath(myPath);
    s.writePath(myNewParent);
  }

  public Path getPath() {
    return myPath;
  }

  public Path getNewParent() {
    return myNewParent;
  }

  @Override
  public void applyTo(RootEntry root) {
    myAffectedEntryIdPaths.add(root.getEntry(myPath).getIdPath());
    root.doMove(myPath, myNewParent);
    myAffectedEntryIdPaths.add(root.getEntry(getNewPath()).getIdPath());
  }

  @Override
  public void revertOn(RootEntry root) {
    root.doMove(getNewPath(), myPath.getParent());
  }

  private Path getNewPath() {
    return myNewParent.appendedWith(myPath.getName());
  }

  @Override
  protected List<IdPath> getAffectedEntryIdPaths() {
    return myAffectedEntryIdPaths;
  }
}
