package com.intellij.history.core.changes;

import com.intellij.history.core.IdPath;
import com.intellij.history.core.storage.Stream;
import com.intellij.history.core.tree.Entry;

import java.io.IOException;

public class ROStatusChange extends StructuralChange {
  private boolean myOldStatus;
  private boolean myNewStatus; // transient

  public ROStatusChange(String path, boolean isReadOnly) {
    super(path);
    myNewStatus = isReadOnly;
  }

  public ROStatusChange(Stream s) throws IOException {
    super(s);
    myOldStatus = s.readBoolean();
  }

  @Override
  public void write(Stream s) throws IOException {
    super.write(s);
    s.writeBoolean(myOldStatus);
  }

  public boolean getOldStatus() {
    return myOldStatus;
  }

  @Override
  protected IdPath doApplyTo(Entry r) {
    Entry e = r.getEntry(myPath);
    myOldStatus = e.isReadOnly();
    e.setReadOnly(myNewStatus);
    return e.getIdPath();
  }

  @Override
  public void revertOn(Entry r) {
    getEntry(r).setReadOnly(myOldStatus);
  }

  private Entry getEntry(Entry r) {
    return r.getEntry(myAffectedIdPath);
  }

  @Override
  public void accept(ChangeVisitor v) throws IOException, ChangeVisitor.StopVisitingException {
    v.visit(this);
  }
}
