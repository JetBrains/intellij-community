package com.intellij.localvcs;

import java.io.IOException;

public class MoveChange extends StructuralChange {
  private String myNewParentPath; // transient
  private IdPath mySecondAffectedIdPath;

  public MoveChange(String path, String newParentPath) {
    super(path);
    myNewParentPath = newParentPath;
  }

  public MoveChange(Stream s) throws IOException {
    super(s);
    mySecondAffectedIdPath = s.readIdPath();
  }

  @Override
  public void write(Stream s) throws IOException {
    super.write(s);
    s.writeIdPath(mySecondAffectedIdPath);
  }

  @Override
  protected IdPath doApplyTo(RootEntry root) {
    IdPath firstIdPath = root.getEntry(myPath).getIdPath();

    root.move(myPath, myNewParentPath);
    mySecondAffectedIdPath = root.getEntry(getNewPath()).getIdPath();

    return firstIdPath;
  }

  private String getNewPath() {
    return Paths.appended(myNewParentPath, Paths.getNameOf(myPath));
  }

  @Override
  public void revertOn(RootEntry root) {
    IdPath newPath = mySecondAffectedIdPath;
    IdPath oldParentPath = myAffectedIdPath.getParent();
    root.move(newPath, oldParentPath);
  }

  @Override
  public IdPath[] getAffectedIdPaths() {
    return new IdPath[]{myAffectedIdPath, mySecondAffectedIdPath};
  }
}
