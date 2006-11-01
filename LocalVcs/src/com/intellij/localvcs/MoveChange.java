package com.intellij.localvcs;

public class MoveChange implements Change {
  private Path myPath;
  private Path myNewParent;

  public MoveChange(Path path, Path newParent) {
    myPath = path;
    myNewParent = newParent;
  }

  public void applyTo(Snapshot snapshot) {
    snapshot.doMove(myPath, myNewParent);
  }

  public void revertOn(Snapshot snapshot) {
    snapshot.doMove(myNewParent.appendedWith(myPath.getName()),
                    myPath.getParent());
  }
}
