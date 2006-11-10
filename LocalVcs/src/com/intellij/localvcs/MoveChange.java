package com.intellij.localvcs;

import java.io.IOException;

public class MoveChange extends Change {
  private Path myPath;
  private Path myNewParent;
  private Integer myAffectedEntryId;

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
    myAffectedEntryId = root.getEntry(myPath).getObjectId();
    root.doMove(myPath, myNewParent);
  }

  @Override
  public void revertOn(RootEntry root) {
    root.doMove(myNewParent.appendedWith(myPath.getName()),
                myPath.getParent());
  }

  @Override
  public Integer getAffectedEntryId() {
    return myAffectedEntryId;
  }
}
