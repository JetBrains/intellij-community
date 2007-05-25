package com.intellij.localvcs.core.changes;

import com.intellij.localvcs.core.IdPath;
import com.intellij.localvcs.core.Paths;
import com.intellij.localvcs.core.storage.Stream;
import com.intellij.localvcs.core.tree.Entry;

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
    doRename(e, myNewName);

    return e.getIdPath();
  }

  @Override
  public void revertOn(Entry r) {
    doRename(r.getEntry(myAffectedIdPath), myOldName);
  }

  private void doRename(Entry e, String newName) {
    e.changeName(Paths.renamed(e.getName(), newName));
  }

  @Override
  public void accept(ChangeVisitor v) throws IOException, ChangeVisitor.StopVisitingException {
    v.visit(this);
  }
}
