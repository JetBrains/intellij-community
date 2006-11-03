package com.intellij.localvcs;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class MoveChange extends Change {
  private Path myPath;
  private Path myNewParent;

  public MoveChange(Path path, Path newParent) {
    myPath = path;
    myNewParent = newParent;
  }

  public MoveChange(DataInputStream s) throws IOException {
    myPath = new Path(s);
    myNewParent = new Path(s);
  }

  @Override
  public void write(DataOutputStream s) throws IOException {
    super.write(s);

    myPath.write(s);
    myNewParent.write(s);
  }

  public Path getPath() {
    return myPath;
  }

  public Path getNewParent() {
    return myNewParent;
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
