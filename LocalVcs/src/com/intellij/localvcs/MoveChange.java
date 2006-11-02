package com.intellij.localvcs;

public class MoveChange extends Change {
  private Path myPath;
  private Path myNewParent;

  public MoveChange(Path path, Path newParent) {
    myPath = path;
    myNewParent = newParent;
  }

  @Override
  public void applyTo(Snapshot snapshot) {
    snapshot.doMove(myPath, myNewParent);
  }

  @Override
  public void revertOn(Snapshot snapshot) {
    snapshot.doMove(myNewParent.appendedWith(myPath.getName()),
                    myPath.getParent());
  }
}
