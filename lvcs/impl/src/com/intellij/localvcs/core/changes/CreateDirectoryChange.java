package com.intellij.localvcs.core.changes;

import com.intellij.localvcs.core.IdPath;
import com.intellij.localvcs.core.storage.Stream;
import com.intellij.localvcs.core.tree.Entry;
import com.intellij.localvcs.core.tree.RootEntry;

import java.io.IOException;

public class CreateDirectoryChange extends StructuralChange {
  private int myId; // transient

  public CreateDirectoryChange(int id, String path) {
    super(path);
    myId = id;
  }

  public CreateDirectoryChange(Stream s) throws IOException {
    super(s);
  }

  @Override
  protected IdPath doApplyTo(RootEntry root) {
    return root.createDirectory(myId, myPath).getIdPath();
  }

  @Override
  public void revertOn(RootEntry root) {
    root.delete(myAffectedIdPath);
  }

  @Override
  public boolean isCreationalFor(Entry e) {
    return e.getId() == myAffectedIdPath.getId();
  }

  @Override
  public void accept(ChangeVisitor v) throws IOException, ChangeVisitor.StopVisitingException {
    v.visit(this);
  }
}
