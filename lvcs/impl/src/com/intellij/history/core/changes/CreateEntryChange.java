package com.intellij.history.core.changes;

import com.intellij.history.core.IdPath;
import com.intellij.history.core.Paths;
import com.intellij.history.core.storage.Stream;
import com.intellij.history.core.tree.Entry;

import java.io.IOException;

public abstract class CreateEntryChange extends StructuralChange {
  protected int myId; // transient

  public CreateEntryChange(int id, String path) {
    super(path);
    myId = id;
  }

  public CreateEntryChange(Stream s) throws IOException {
    super(s);
  }

  protected String getEntryParentPath() {
    return Paths.getParentOf(myPath);
  }

  protected String getEntryName() {
    // new String() is for trimming rest part of path to
    // minimaze memory usage after bulk updates and refreshes.
    return new String(Paths.getNameOf(myPath));
  }

  protected IdPath addEntry(Entry r, String parentPath, Entry e) {
    Entry parent = parentPath == null ? r : r.getEntry(parentPath);
    parent.addChild(e);
    return e.getIdPath();
  }

  @Override
  public void revertOn(Entry root) {
    Entry e = root.getEntry(myAffectedIdPath);
    removeEntry(e);
  }

  @Override
  public boolean isCreationalFor(Entry e) {
    return isCreationalFor(e.getIdPath());
  }

  public boolean isCreationalFor(IdPath p) {
    return p.getId() == myAffectedIdPath.getId();
  }

  @Override
  public void accept(ChangeVisitor v) throws IOException, ChangeVisitor.StopVisitingException {
    v.visit(this);
  }
}
