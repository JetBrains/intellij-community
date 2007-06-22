package com.intellij.history.core.changes;

import com.intellij.history.core.IdPath;
import com.intellij.history.core.storage.Stream;
import com.intellij.history.core.tree.Entry;

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

    removeEntry(e);

    Entry newParent = r.getEntry(myNewParentPath);
    newParent.addChild(e);

    myTargetIdPath = e.getIdPath();

    return firstIdPath;
  }

  @Override
  public void revertOn(Entry r) {
    Entry e = getEntry(r);
    removeEntry(e);

    Entry oldParent = getOldParent(r);
    oldParent.addChild(e);
  }

  @Override
  public boolean canRevertOn(Entry r) {
    return hasNoSuchEntry(getOldParent(r), getEntry(r).getName());
  }

  private Entry getEntry(Entry r) {
    return r.getEntry(myTargetIdPath);
  }

  private Entry getOldParent(Entry r) {
    return r.getEntry(myAffectedIdPath.getParent());
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
