package com.intellij.localvcs.core.changes;

import com.intellij.localvcs.core.IdPath;
import com.intellij.localvcs.core.storage.Stream;
import com.intellij.localvcs.core.tree.Entry;

import java.io.IOException;

public class MoveChange extends StructuralChange {
  private String myNewParentPath; // transient
  private IdPath myTargetIdPath;

  public MoveChange(String path, String newParentPath) {
    super(path);
    myNewParentPath = newParentPath;
  }

  public MoveChange(Stream s) throws IOException {
    super(s);
    myTargetIdPath = s.readIdPath();
  }

  @Override
  public void write(Stream s) throws IOException {
    super.write(s);
    s.writeIdPath(myTargetIdPath);
  }

  @Override
  protected IdPath doApplyTo(Entry r) {
    Entry e = r.getEntry(myPath);
    IdPath firstIdPath = e.getIdPath();

    e.getParent().removeChild(e);
    Entry newParent = r.getEntry(myNewParentPath);
    newParent.addChild(e);

    myTargetIdPath = e.getIdPath();

    return firstIdPath;
  }

  @Override
  public void revertOn(Entry r) {
    Entry e = r.getEntry(myTargetIdPath);
    Entry oldParent = r.getEntry(myAffectedIdPath.getParent());

    e.getParent().removeChild(e);
    oldParent.addChild(e);
  }

  @Override
  public IdPath[] getAffectedIdPaths() {
    return new IdPath[]{myAffectedIdPath, myTargetIdPath};
  }

  @Override
  public void accept(ChangeVisitor v) throws IOException, ChangeVisitor.StopVisitingException {
    v.visit(this);
  }
}
