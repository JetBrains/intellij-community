package com.intellij.history.core.changes;

import com.intellij.history.core.IdPath;
import com.intellij.history.core.Paths;
import com.intellij.history.core.storage.Stream;
import com.intellij.history.core.tree.Entry;

import java.io.IOException;

public abstract class CreateEntryChange<NON_APPLIED_STATE_TYPE extends CreateEntryChangeNonAppliedState>
    extends StructuralChange<NON_APPLIED_STATE_TYPE, StructuralChangeAppliedState> {

  public CreateEntryChange(int id, String path) {
    super(path);
    getNonAppliedState().myId = id;
  }

  public CreateEntryChange(Stream s) throws IOException {
    super(s);
  }

  @Override
  protected NON_APPLIED_STATE_TYPE createNonAppliedState() {
    return (NON_APPLIED_STATE_TYPE)new CreateEntryChangeNonAppliedState();
  }

  @Override
  protected StructuralChangeAppliedState createAppliedState() {
    return new StructuralChangeAppliedState();
  }

  protected String getEntryParentPath() {
    return Paths.getParentOf(getPath());
  }

  protected String getEntryName() {
    // new String() is for trimming rest part of path to
    // minimaze memory usage after bulk updates and refreshes.
    return new String(Paths.getNameOf(getPath()));
  }

  protected IdPath addEntry(Entry r, String parentPath, Entry e) {
    Entry parent = parentPath == null ? r : r.getEntry(parentPath);
    parent.addChild(e);
    return e.getIdPath();
  }

  @Override
  public void revertOn(Entry root) {
    Entry e = root.getEntry(getAffectedIdPath());
    removeEntry(e);
  }

  @Override
  public boolean isCreationalFor(Entry e) {
    return isCreationalFor(e.getIdPath());
  }

  public boolean isCreationalFor(IdPath p) {
    return p.getId() == getAffectedIdPath().getId();
  }

  @Override
  public void accept(ChangeVisitor v) throws IOException, ChangeVisitor.StopVisitingException {
    v.visit(this);
  }
}
