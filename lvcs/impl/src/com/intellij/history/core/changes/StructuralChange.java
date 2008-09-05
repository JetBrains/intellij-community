package com.intellij.history.core.changes;

import com.intellij.history.core.IdPath;
import com.intellij.history.core.storage.Content;
import com.intellij.history.core.storage.Stream;
import com.intellij.history.core.tree.Entry;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

public abstract class StructuralChange<
    NON_APPLIED_STATE_TYPE extends StructuralChangeNonAppliedState,
    APPLIED_STATE_TYPE extends StructuralChangeAppliedState> extends Change {
  private StructuralChangeState myState;

  protected StructuralChange(String path) {
    myState = createNonAppliedState();
    getNonAppliedState().myPath = path;
  }

  protected StructuralChange(Stream s) throws IOException {
    myState = createAppliedState();
    getAppliedState().myAffectedIdPath = s.readIdPath();
  }

  public void write(Stream s) throws IOException {
    s.writeIdPath(getAffectedIdPath());
  }

  protected String getPath() {
    return getNonAppliedState().myPath;
  }

  protected IdPath getAffectedIdPath() {
    return getAppliedState().myAffectedIdPath;
  }

  protected abstract NON_APPLIED_STATE_TYPE createNonAppliedState();

  protected abstract APPLIED_STATE_TYPE createAppliedState();

  protected NON_APPLIED_STATE_TYPE getNonAppliedState() {
    return (NON_APPLIED_STATE_TYPE)myState;
  }

  protected APPLIED_STATE_TYPE getAppliedState() {
    return (APPLIED_STATE_TYPE)myState;
  }

  @Override
  public void applyTo(Entry r) {
    APPLIED_STATE_TYPE newState = createAppliedState();
    newState.myAffectedIdPath = doApplyTo(r, newState);
    myState = newState;
  }

  protected abstract IdPath doApplyTo(Entry r, APPLIED_STATE_TYPE newState);

  @Override
  public abstract void revertOn(Entry r);

  protected boolean hasNoSuchEntry(Entry parent, String name) {
    return parent.findChild(name) == null;
  }

  protected void removeEntry(Entry e) {
    e.getParent().removeChild(e);
  }

  @Override
  protected boolean affects(IdPath... pp) {
    for (IdPath p1 : getAffectedIdPaths()) {
      for (IdPath p2 : pp) {
        if (p1.isChildOrParentOf(p2)) return true;
      }
    }
    return false;
  }

  @Override
  public boolean affectsOnlyInside(Entry e) {
    for (IdPath p : getAffectedIdPaths()) {
      if (!p.startsWith(e.getIdPath())) {
        return false;
      }
    }
    return true;
  }

  @Override
  public boolean affectsSameAs(List<Change> cc) {
    for (Change c : cc) {
      if (c.affects(getAffectedIdPaths())) return true;
    }
    return false;
  }

  @Override
  public boolean isCreationalFor(Entry e) {
    return false;
  }

  public IdPath[] getAffectedIdPaths() {
    return new IdPath[]{getAffectedIdPath()};
  }

  @Override
  public List<Content> getContentsToPurge() {
    return Collections.emptyList();
  }
}
