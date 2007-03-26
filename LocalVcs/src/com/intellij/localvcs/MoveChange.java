package com.intellij.localvcs;

import java.io.IOException;

public class MoveChange extends Change {
  private String myNewParentPath;
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
  protected void doApplyTo(RootEntry root) {
    setFirstAffectedIdPath(root.getEntry(myPath).getIdPath());
    root.move(myPath, myNewParentPath);
    setSecondAffectedIdPath(root.getEntry(getNewPath()).getIdPath());
  }

  private String getNewPath() {
    return Paths.appended(myNewParentPath, Paths.getNameOf(myPath));
  }

  @Override
  public void revertOn(RootEntry root) {
    IdPath newPath = getSecondAffectedIdPath();
    IdPath oldParentPath = getFirstAffectedIdPath().getParent();
    root.move(newPath, oldParentPath);
  }

  @Override
  public boolean affects(Entry e) {
    return super.affects(e) || mySecondAffectedIdPath.contains(e.getId());
  }

  protected void setFirstAffectedIdPath(IdPath p) {
    setAffectedIdPath(p);
  }

  protected void setSecondAffectedIdPath(IdPath p) {
    mySecondAffectedIdPath = p;
  }

  protected IdPath getFirstAffectedIdPath() {
    return getAffectedIdPath();
  }

  protected IdPath getSecondAffectedIdPath() {
    return mySecondAffectedIdPath;
  }
}
