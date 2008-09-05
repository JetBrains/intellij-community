package com.intellij.history.core.changes;

import com.intellij.history.core.IdPath;
import com.intellij.history.core.Paths;
import com.intellij.history.core.storage.Stream;
import com.intellij.history.core.tree.Entry;

import java.io.IOException;

public class RenameChange extends StructuralChange<RenameChangeNonAppliedState, RenameChangeAppliedState> {
  public RenameChange(String path, String newName) {
    super(path);
    getNonAppliedState().myNewName = newName;
  }

  public RenameChange(Stream s) throws IOException {
    super(s);
    getAppliedState().myOldName = s.readString();
  }

  @Override
  public void write(Stream s) throws IOException {
    super.write(s);
    s.writeString(getAppliedState().myOldName);
  }

  @Override
  protected RenameChangeAppliedState createAppliedState() {
    return new RenameChangeAppliedState();
  }

  @Override
  protected RenameChangeNonAppliedState createNonAppliedState() {
    return new RenameChangeNonAppliedState();
  }

  public String getOldName() {
    return getAppliedState().myOldName;
  }

  @Override
  protected IdPath doApplyTo(Entry r, RenameChangeAppliedState newState) {
    Entry e = r.getEntry(getPath());

    // todo one more hack to support roots...
    // todo i defitilety have to do something with it...
    newState.myOldName = Paths.getNameOf(e.getName());
    rename(e, getNonAppliedState().myNewName);

    return e.getIdPath();
  }

  @Override
  public void revertOn(Entry r) {
    rename(getEntry(r), getAppliedState().myOldName);
  }

  @Override
  public boolean canRevertOn(Entry r) {
    return hasNoSuchEntry(getEntry(r).getParent(), getAppliedState().myOldName);
  }

  private Entry getEntry(Entry r) {
    return r.getEntry(getAffectedIdPath());
  }

  private void rename(Entry e, String newName) {
    e.changeName(Paths.renamed(e.getName(), newName));
  }

  @Override
  public void accept(ChangeVisitor v) throws IOException, ChangeVisitor.StopVisitingException {
    v.visit(this);
  }
}
