package com.intellij.localvcs.core.changes;

import com.intellij.localvcs.core.IdPath;
import com.intellij.localvcs.core.Paths;
import com.intellij.localvcs.core.storage.Stream;
import com.intellij.localvcs.core.tree.Entry;
import com.intellij.localvcs.core.tree.RootEntry;

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
  protected IdPath doApplyTo(RootEntry root) {
    Entry e = root.getEntry(myPath);

    // todo one more hack to support roots...
    // todo i defitilety have to do something with it...
    myOldName = Paths.getNameOf(e.getName());

    IdPath idPath = e.getIdPath();
    root.rename(idPath, myNewName);
    return idPath;
  }

  @Override
  public void revertOn(RootEntry root) {
    root.rename(myAffectedIdPath, myOldName);
  }

  @Override
  public void accept(ChangeVisitor v) throws IOException, ChangeVisitor.StopVisitingException {
    v.visit(this);
  }
}
