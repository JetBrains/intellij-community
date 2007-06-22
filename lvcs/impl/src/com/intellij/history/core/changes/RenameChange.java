package com.intellij.history.core.changes;

import com.intellij.history.core.IdPath;
import com.intellij.history.core.Paths;
import com.intellij.history.core.storage.Stream;
import com.intellij.history.core.tree.Entry;

import java.io.IOException;

public class RenameChange extends StructuralChange {
  private String myOldName;
  private String myNewName; // transient

  public RenameChange(String path, String newName) {
    super(path);
    myNewName = newName;
  }

  public RenameChange(Stream s) throws IOException {
    super(s);
    myOldName = s.readString();
  }

  @Override
  public void write(Stream s) throws IOException {
    super.write(s);
    s.writeString(myOldName);
  }

  public String getOldName() {
    return myOldName;
  }

  @Override
  protected IdPath doApplyTo(Entry r) {
    Entry e = r.getEntry(myPath);

    // todo one more hack to support roots...
    // todo i defitilety have to do something with it...
    myOldName = Paths.getNameOf(e.getName());
    rename(e, myNewName);

    return e.getIdPath();
  }

  @Override
  public void revertOn(Entry r) {
    rename(getEntry(r), myOldName);
  }

  @Override
  public boolean canRevertOn(Entry r) {
    return hasNoSuchEntry(getEntry(r).getParent(), myOldName);
  }

  private Entry getEntry(Entry r) {
    return r.getEntry(myAffectedIdPath);
  }

  private void rename(Entry e, String newName) {
    e.changeName(Paths.renamed(e.getName(), newName));
  }

  @Override
  public void accept(ChangeVisitor v) throws IOException, ChangeVisitor.StopVisitingException {
    v.visit(this);
  }
}
