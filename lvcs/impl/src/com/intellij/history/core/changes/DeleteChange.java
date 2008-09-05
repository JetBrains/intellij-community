package com.intellij.history.core.changes;

import com.intellij.history.core.IdPath;
import com.intellij.history.core.storage.Content;
import com.intellij.history.core.storage.Stream;
import com.intellij.history.core.tree.Entry;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class DeleteChange extends StructuralChange<StructuralChangeNonAppliedState, DeleteChangeAppliedState> {
  public DeleteChange(String path) {
    super(path);
  }

  public DeleteChange(Stream s) throws IOException {
    super(s);
    getAppliedState().myAffectedEntry = s.readEntry();
  }

  @Override
  public void write(Stream s) throws IOException {
    super.write(s);
    s.writeEntry(getAppliedState().myAffectedEntry);
  }

  @Override
  protected StructuralChangeNonAppliedState createNonAppliedState() {
    return new StructuralChangeNonAppliedState();
  }

  @Override
  protected DeleteChangeAppliedState createAppliedState() {
    return new DeleteChangeAppliedState();
  }

  public Entry getAffectedEntry() {
    return getAppliedState().myAffectedEntry;
  }

  @Override
  protected IdPath doApplyTo(Entry r, DeleteChangeAppliedState newState) {
    newState.myAffectedEntry = r.getEntry(getPath());
    IdPath idPath = newState.myAffectedEntry.getIdPath();

    removeEntry(newState.myAffectedEntry);

    return idPath;
  }

  @Override
  public void revertOn(Entry r) {
    Entry parent = getParent(r);
    parent.addChild(getAppliedState().myAffectedEntry.copy());
  }

  @Override
  public boolean canRevertOn(Entry r) {
    return hasNoSuchEntry(getParent(r), getAppliedState().myAffectedEntry.getName());
  }

  private Entry getParent(Entry r) {
    return r.getEntry(getAffectedIdPath().getParent());
  }

  public boolean isDeletionOf(IdPath p) {
    return p.startsWith(getAffectedIdPath());
  }

  @Override
  public List<Content> getContentsToPurge() {
    List<Content> result = new ArrayList<Content>();
    collectContentsRecursively(getAppliedState().myAffectedEntry, result);
    return result;
  }

  private void collectContentsRecursively(Entry e, List<Content> result) {
    if (e.isDirectory()) {
      for (Entry child : e.getChildren()) {
        collectContentsRecursively(child, result);
      }
    }
    else {
      result.add(e.getContent());
    }
  }

  @Override
  public void accept(ChangeVisitor v) throws IOException, ChangeVisitor.StopVisitingException {
    v.visit(this);
  }

}
